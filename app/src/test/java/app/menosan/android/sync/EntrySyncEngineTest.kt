package app.menosan.android.sync

import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.network.ApiResult
import app.menosan.android.data.local.SyncState
import app.menosan.android.data.remote.dto.EntryListDto
import app.menosan.android.data.remote.dto.SyncOp
import app.menosan.android.data.remote.dto.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class EntrySyncEngineTest {
    // Tue 2026-09-29 10:00 PHT → current week starts Sun 2026-09-27.
    private val now: Instant = Instant.parse("2026-09-29T02:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val thisWeek = LocalDate.parse("2026-09-27")
    private val dao = FakeEntryDao()
    private val remote = FakeEntryRemote { now.plusSeconds(5) }
    private val notices = FakeSyncNotices()
    private val engine = EntrySyncEngine(dao, remote, DirectTransactions, notices, clock)

    private fun at(minutesAgo: Long) = now.minusSeconds(minutesAgo * 60)

    @Test
    fun `an empty outbox makes no request`() = runTest {
        dao.put(entity("a", at(5), SyncState.SYNCED))
        val outcome = engine.pushOutbox()
        assertTrue(outcome is PushOutcome.Done)
        assertTrue(remote.requests.isEmpty())
    }

    @Test
    fun `pending creates and updates are upserts and pending deletes are deletes`() = runTest {
        dao.put(
            entity("c", at(30), SyncState.PENDING_CREATE),
            entity("u", at(20), SyncState.PENDING_UPDATE),
            entity("d", at(10), SyncState.PENDING_DELETE),
            entity("s", at(5), SyncState.SYNCED),
        )
        val outcome = engine.pushOutbox()

        assertEquals(PushOutcome.Done(PushStats(sent = 3, synced = 3)), outcome)
        val request = remote.requests.single()
        assertEquals(listOf("c", "u"), request.upserts.map { it.id })
        assertEquals(listOf("d"), request.deletes)
        assertEquals(SyncState.SYNCED, dao.rows.value.getValue("c").syncState)
        assertEquals(SyncState.SYNCED, dao.rows.value.getValue("u").syncState)
        assertNull("an OK delete removes the row", dao.rows.value["d"])
    }

    @Test
    fun `an OK upsert takes the server copy`() = runTest {
        dao.put(entity("c", at(30), name = "Coffee sachet  "))
        engine.pushOutbox()
        val row = dao.rows.value.getValue("c")
        assertEquals("Coffee sachet", row.name)
        assertEquals(now.plusSeconds(5), row.updatedAt)
        assertEquals(thisWeek, row.weekStart)
        assertNull(row.lastError)
    }

    @Test
    fun `the outbox is sent in batches of at most 500`() = runTest {
        val base = at(60 * 24)
        repeat(1201) { i -> dao.put(entity("e$i", base.plusSeconds(i.toLong()))) }
        val outcome = engine.pushOutbox()

        assertEquals(listOf(500, 500, 201), remote.requests.map { it.upserts.size + it.deletes.size })
        assertEquals(1201, outcome.stats.synced)
        assertTrue(dao.all.all { it.syncState == SyncState.SYNCED })
    }

    @Test
    fun `WEEK_CLOSED on an update reverts to the server copy and leaves a notice`() = runTest {
        val local = entity("u", at(30), SyncState.PENDING_UPDATE, name = "Edited", quantity = 9)
        dao.put(local)
        remote.statusFor = { _, _ -> SyncStatus.WEEK_CLOSED }
        remote.serverCopy = { local.toServerDto(name = "Original", quantity = 1) }

        val outcome = engine.pushOutbox()

        assertTrue(outcome is PushOutcome.Done)
        val row = dao.rows.value.getValue("u")
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals("Original", row.name)
        assertEquals(1, row.quantity)
        assertEquals(1, notices.revertedChanges.value)
    }

    @Test
    fun `WEEK_CLOSED on a delete restores the entry`() = runTest {
        val local = entity("d", at(30), SyncState.PENDING_DELETE)
        dao.put(local)
        remote.statusFor = { _, _ -> SyncStatus.WEEK_CLOSED }
        remote.serverCopy = { local.toServerDto() }

        engine.pushOutbox()

        assertEquals(SyncState.SYNCED, dao.rows.value.getValue("d").syncState)
        assertEquals(1, notices.revertedChanges.value)
    }

    @Test
    fun `final errors keep the row as couldn't sync and it is not resent`() = runTest {
        dao.put(entity("i", at(30)), entity("t", at(20)), entity("x", at(10)))
        remote.statusFor = { id, _ ->
            when (id) {
                "i" -> SyncStatus.INVALID
                "t" -> SyncStatus.INVALID_TIMESTAMP
                else -> SyncStatus.CONFLICT
            }
        }
        val outcome = engine.pushOutbox()

        assertEquals(PushOutcome.Done(PushStats(sent = 3, failed = 3, conflicts = 1)), outcome)
        dao.all.forEach {
            assertEquals(SyncState.PENDING_CREATE, it.syncState)
            assertEquals("Nope (${remote.statusFor(it.id, SyncOp.UPSERT)}).", it.lastError)
        }

        engine.pushOutbox()
        assertEquals("rows with lastError wait for the user", 1, remote.requests.size)
    }

    @Test
    fun `ERROR and unknown statuses keep the row pending and ask for a retry`() = runTest {
        dao.put(entity("e", at(30)), entity("u", at(20)), entity("ok", at(10)))
        remote.statusFor = { id, _ ->
            when (id) {
                "e" -> SyncStatus.ERROR
                "u" -> SyncStatus.UNKNOWN
                else -> SyncStatus.OK
            }
        }
        val outcome = engine.pushOutbox()

        assertTrue(outcome is PushOutcome.RetryLater)
        assertEquals(1, remote.requests.size)
        assertEquals(SyncState.PENDING_CREATE, dao.rows.value.getValue("e").syncState)
        assertNull(dao.rows.value.getValue("e").lastError)
        assertEquals(SyncState.PENDING_CREATE, dao.rows.value.getValue("u").syncState)
        assertEquals(SyncState.SYNCED, dao.rows.value.getValue("ok").syncState)
    }

    @Test
    fun `missing results are retried, never treated as success`() = runTest {
        dao.put(entity("a", at(30)))
        remote.dropResults = true
        assertTrue(engine.pushOutbox() is PushOutcome.RetryLater)
        assertEquals(SyncState.PENDING_CREATE, dao.rows.value.getValue("a").syncState)
    }

    @Test
    fun `network errors, 5xx, and a missing account retry later without touching rows`() = runTest {
        val before = entity("a", at(30))
        dao.put(before)
        val retryable = listOf(
            ApiError.Network(IOException("offline")),
            FakeEntryRemote.http(503, ApiErrorCode.UNKNOWN),
            FakeEntryRemote.http(500, ApiErrorCode.INTERNAL),
            FakeEntryRemote.http(404, ApiErrorCode.ACCOUNT_NOT_FOUND),
            FakeEntryRemote.http(429, ApiErrorCode.RATE_LIMITED),
        )
        for (error in retryable) {
            remote.syncFailure = error
            assertTrue(error.toString(), engine.pushOutbox() is PushOutcome.RetryLater)
            assertEquals(before, dao.rows.value.getValue("a"))
        }
    }

    @Test
    fun `a 401 or a refused request stops until the next trigger`() = runTest {
        dao.put(entity("a", at(30)))
        remote.syncFailure = FakeEntryRemote.http(401, ApiErrorCode.UNAUTHENTICATED)
        assertTrue(engine.pushOutbox() is PushOutcome.Stopped)
        remote.syncFailure = FakeEntryRemote.http(400, ApiErrorCode.VALIDATION_FAILED)
        assertTrue(engine.pushOutbox() is PushOutcome.Stopped)
        assertEquals(SyncState.PENDING_CREATE, dao.rows.value.getValue("a").syncState)
    }

    @Test
    fun `an edit made while the request is in flight is not overwritten and is sent next`() = runTest {
        val sent = entity("a", at(30))
        dao.put(sent)
        var edited = false
        remote.duringSync = {
            if (!edited) {
                edited = true
                dao.put(sent.copy(name = "Edited", updatedAt = now, syncState = SyncState.PENDING_CREATE))
            }
        }
        val outcome = engine.pushOutbox()

        assertEquals(2, remote.requests.size)
        assertEquals("Edited", remote.requests[1].upserts.single().name)
        assertTrue(outcome is PushOutcome.Done)
        val row = dao.rows.value.getValue("a")
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals("Edited", row.name)
    }

    @Test
    fun `a delete made while the create is in flight still reaches the server`() = runTest {
        val sent = entity("a", at(30))
        dao.put(sent)
        var deleted = false
        remote.duringSync = {
            if (!deleted) {
                deleted = true
                dao.put(sent.copy(syncState = SyncState.PENDING_DELETE, updatedAt = now))
            }
        }
        engine.pushOutbox()

        assertEquals(listOf("a"), remote.requests[1].deletes)
        assertNull(dao.rows.value["a"])
    }

    @Test
    fun `rows cleared during the request are not recreated`() = runTest {
        dao.put(entity("a", at(30)))
        remote.duringSync = { dao.rows.value = emptyMap() }
        engine.pushOutbox()
        assertTrue(dao.all.isEmpty())
    }

    @Test
    fun `pull merges the current week - server wins for synced rows, local wins for pending rows`() = runTest {
        val syncedChanged = entity("s1", at(50), SyncState.SYNCED, name = "Old name")
        val syncedGone = entity("s2", at(40), SyncState.SYNCED)
        val pendingUpdate = entity("p1", at(30), SyncState.PENDING_UPDATE, name = "My edit")
        val pendingDelete = entity("p2", at(20), SyncState.PENDING_DELETE)
        val pendingCreate = entity("p3", at(10), SyncState.PENDING_CREATE)
        val lastWeek = entity("old", now.minusSeconds(8 * 24 * 3600), SyncState.SYNCED)
        dao.put(syncedChanged, syncedGone, pendingUpdate, pendingDelete, pendingCreate, lastWeek)
        val fromOtherDevice = entity("n1", at(15), SyncState.SYNCED)
        remote.currentWeek = ApiResult.Success(
            EntryListDto(
                weekStart = thisWeek,
                weekEnd = thisWeek.plusDays(6),
                editable = true,
                entries = listOf(
                    syncedChanged.toServerDto(name = "New name"),
                    pendingUpdate.toServerDto(name = "Server name"),
                    pendingDelete.toServerDto(),
                    fromOtherDevice.toServerDto(),
                ),
            ),
            200,
        )

        assertTrue(engine.pullCurrentWeek())

        val rows = dao.rows.value
        assertEquals("New name", rows.getValue("s1").name)
        assertNull("synced rows the server no longer has are removed", rows["s2"])
        assertEquals("My edit", rows.getValue("p1").name)
        assertEquals(SyncState.PENDING_UPDATE, rows.getValue("p1").syncState)
        assertEquals(SyncState.PENDING_DELETE, rows.getValue("p2").syncState)
        assertEquals(SyncState.PENDING_CREATE, rows.getValue("p3").syncState)
        assertEquals(SyncState.SYNCED, rows.getValue("n1").syncState)
        assertEquals("other weeks are untouched", lastWeek, rows["old"])
    }

    @Test
    fun `pull does nothing when offline`() = runTest {
        val row = entity("s1", at(50), SyncState.SYNCED)
        dao.put(row)
        assertFalse(engine.pullCurrentWeek())
        assertEquals(row, dao.rows.value["s1"])
    }

    @Test
    fun `retention keeps the current and two previous weeks and never prunes pending rows`() = runTest {
        val day = 24 * 3600L
        dao.put(
            entity("w0", at(10), SyncState.SYNCED),
            entity("w1", now.minusSeconds(7 * day), SyncState.SYNCED),
            entity("w2", now.minusSeconds(14 * day), SyncState.SYNCED),
            entity("w3", now.minusSeconds(21 * day), SyncState.SYNCED),
            entity("w3pending", now.minusSeconds(21 * day), SyncState.PENDING_CREATE),
        )
        assertEquals(LocalDate.parse("2026-09-13"), engine.oldestKeptWeekStart())
        assertEquals(1, engine.pruneOldEntries())
        assertEquals(setOf("w0", "w1", "w2", "w3pending"), dao.rows.value.keys)
    }
}
