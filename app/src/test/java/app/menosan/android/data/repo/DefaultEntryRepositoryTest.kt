package app.menosan.android.data.repo

import app.menosan.android.core.model.EntryDraft
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.EntrySyncStatus
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.data.local.SyncState
import app.menosan.android.sync.CountingSyncRequester
import app.menosan.android.sync.DirectTransactions
import app.menosan.android.sync.EntrySyncEngine
import app.menosan.android.sync.FakeEntryDao
import app.menosan.android.sync.FakeEntryRemote
import app.menosan.android.sync.FakeSyncNotices
import app.menosan.android.sync.entity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEntryRepositoryTest {
    private val taxonomy = TaxonomyRepository.parseTaxonomy(File("src/main/assets/taxonomy.json").readText())

    // Tue 2026-09-29 10:00 PHT → current week starts Sun 2026-09-27.
    private val now: Instant = Instant.parse("2026-09-29T02:00:00.123456Z")
    private val dao = FakeEntryDao()
    private val requester = CountingSyncRequester()

    private fun repository(clock: Clock = Clock.fixed(now, ZoneOffset.UTC)): DefaultEntryRepository {
        val engine = EntrySyncEngine(dao, FakeEntryRemote { now }, DirectTransactions, FakeSyncNotices(), clock)
        return DefaultEntryRepository(dao, { taxonomy }, clock, requester, engine)
    }

    private val draft = EntryDraft(name = "  Coffee 3-in-1  ", subcategory = "RES_SACHETS", quantity = 3, source = EntrySource.MANUAL)

    @Test
    fun `create saves a pending row with a UUID, ms createdAt, derived category and week, then requests a sync`() = runTest {
        val entry = repository().create(draft)

        val row = dao.rows.value.getValue(entry.id)
        assertEquals(36, entry.id.length)
        assertEquals("Coffee 3-in-1", row.name)
        assertEquals(WasteCategory.RESIDUAL, row.category)
        assertEquals(Instant.parse("2026-09-29T02:00:00.123Z"), row.createdAt)
        assertEquals(LocalDate.parse("2026-09-27"), row.weekStart)
        assertEquals(SyncState.PENDING_CREATE, row.syncState)
        assertEquals(EntrySyncStatus.PENDING, entry.syncStatus)
        assertTrue(entry.editable)
        assertEquals(1, requester.requests)
    }

    @Test
    fun `create rejects invalid drafts without writing`() = runTest {
        val repo = repository()
        for ((bad, field) in listOf(
            draft.copy(name = "   ") to "name",
            draft.copy(name = "x".repeat(61)) to "name",
            draft.copy(quantity = 0) to "quantity",
            draft.copy(quantity = 1000) to "quantity",
            draft.copy(subcategory = "NOPE") to "subcategory",
        )) {
            try {
                repo.create(bad)
                fail("expected Invalid($field)")
            } catch (e: EntryChangeException.Invalid) {
                assertEquals(field, e.field)
            }
        }
        assertTrue(dao.all.isEmpty())
        assertEquals(0, requester.requests)
    }

    @Test
    fun `update keeps createdAt and source, clears the last error, and marks the row for sync`() = runTest {
        val synced = entity("a", now.minusSeconds(3600), SyncState.SYNCED)
        val failed = entity("b", now.minusSeconds(1800), SyncState.PENDING_CREATE, lastError = "Nope")
        dao.put(synced, failed)
        val repo = repository()

        repo.update("a", draft.copy(subcategory = "BIO_FOOD_LEFTOVERS", quantity = 5))
        repo.update("b", draft)

        val a = dao.rows.value.getValue("a")
        assertEquals(SyncState.PENDING_UPDATE, a.syncState)
        assertEquals(WasteCategory.BIODEGRADABLE, a.category)
        assertEquals(synced.createdAt, a.createdAt)
        assertEquals(5, a.quantity)
        val b = dao.rows.value.getValue("b")
        assertEquals(SyncState.PENDING_CREATE, b.syncState)
        assertNull(b.lastError)
        assertEquals(2, requester.requests)
    }

    @Test
    fun `past-week entries can't be edited or deleted`() = runTest {
        dao.put(entity("old", now.minusSeconds(8 * 24 * 3600), SyncState.SYNCED))
        val repo = repository()
        assertFalse(repo.get("old")!!.editable)
        try {
            repo.update("old", draft)
            fail("expected WeekClosed")
        } catch (_: EntryChangeException.WeekClosed) {
        }
        try {
            repo.delete("old")
            fail("expected WeekClosed")
        } catch (_: EntryChangeException.WeekClosed) {
        }
        assertEquals(SyncState.SYNCED, dao.rows.value.getValue("old").syncState)
    }

    @Test
    fun `delete leaves a hidden tombstone, even for a create that was never confirmed`() = runTest {
        dao.put(entity("s", now.minusSeconds(60), SyncState.SYNCED), entity("p", now.minusSeconds(30), SyncState.PENDING_CREATE))
        val repo = repository()
        repo.delete("s")
        repo.delete("p")

        assertTrue(dao.all.all { it.syncState == SyncState.PENDING_DELETE })
        assertNull(repo.get("s"))
        assertTrue(repo.observeWeek(LocalDate.parse("2026-09-27")).first().isEmpty())
        assertTrue(repo.hasPendingChanges())
        try {
            repo.delete("s")
            fail("expected NotFound")
        } catch (_: EntryChangeException.NotFound) {
        }
    }

    @Test
    fun `sync status maps synced, pending, and couldn't sync`() = runTest {
        dao.put(
            entity("s", now.minusSeconds(60), SyncState.SYNCED),
            entity("p", now.minusSeconds(50), SyncState.PENDING_UPDATE),
            entity("f", now.minusSeconds(40), SyncState.PENDING_CREATE, lastError = "Nope"),
        )
        val byId = repository().observeCurrentWeek().first().associateBy { it.id }
        assertEquals(EntrySyncStatus.SYNCED, byId.getValue("s").syncStatus)
        assertEquals(EntrySyncStatus.PENDING, byId.getValue("p").syncStatus)
        assertEquals(EntrySyncStatus.FAILED, byId.getValue("f").syncStatus)
        assertEquals("Nope", byId.getValue("f").lastError)
    }

    @Test
    fun `observeCurrentWeek follows the rollover at Sunday 00-00 Manila`() = runTest {
        // Sat 2026-10-03 23:59:00 PHT; the clock follows the test's virtual time.
        val start = Instant.parse("2026-10-03T15:59:00Z")
        val clock = VirtualClock(this, start)
        dao.put(
            entity("sat", Instant.parse("2026-10-03T15:00:00Z"), SyncState.SYNCED),
            entity("sun", Instant.parse("2026-10-03T16:00:30Z"), SyncState.SYNCED),
        )
        val weeks = repository(clock).observeCurrentWeek().take(2).toList()

        assertEquals(listOf("sat"), weeks[0].map { it.id })
        assertEquals(listOf("sun"), weeks[1].map { it.id })
        assertTrue(weeks[1].single().editable)
    }

    @Test
    fun `currentWeekStartFlow uses Manila, not the device zone`() = runTest {
        // Sun 2026-09-27 00:30 PHT is still Saturday in UTC and in most device zones west of Manila.
        val clock = Clock.fixed(Instant.parse("2026-09-26T16:30:00Z"), ZoneId.of("America/Los_Angeles"))
        assertEquals(LocalDate.parse("2026-09-27"), currentWeekStartFlow(clock).first())
    }

    private class VirtualClock(private val scope: TestScope, private val start: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = start.plusMillis(scope.testScheduler.currentTime)
    }
}
