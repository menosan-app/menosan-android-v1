package app.menosan.android.data.repo

import app.menosan.android.data.local.SyncState
import app.menosan.android.data.repo.ReportFixtures.CLOCK
import app.menosan.android.data.repo.ReportFixtures.LATEST_WEEK
import app.menosan.android.data.repo.ReportFixtures.NOW
import app.menosan.android.data.repo.ReportFixtures.PREVIOUS_WEEK
import app.menosan.android.data.repo.ReportFixtures.TAXONOMY
import app.menosan.android.data.repo.ReportFixtures.entry
import app.menosan.android.data.repo.ReportFixtures.recommendation
import app.menosan.android.data.repo.ReportFixtures.serverReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** [DefaultReportRepository]: caching, offline fallback and replacement (plan §5.7), and adoption (plan §6.3, I7). */
class ReportRepositoryTest {
    private val api = FakeReportsApi()
    private val cache = FakeReportCacheDao()
    private val entries = FakeEntrySource()

    private fun repository(clock: Clock = CLOCK) =
        DefaultReportRepository(api, cache, entries, LocalReportGenerator(entries, cache, { TAXONOMY }, clock), clock)

    private val repo = repository()

    private val olderWeek = PREVIOUS_WEEK.minusDays(7)

    private fun latestReport(vararg recs: app.menosan.android.data.remote.dto.RecommendationDto) =
        serverReport(LATEST_WEEK, listOf("RES_SACHETS" to 8), mapOf("RES_SACHETS" to recs.toList()))

    @Test
    fun `refresh caches the list and the full reports of the newest two`() = runTest {
        api.reports[LATEST_WEEK] = latestReport(recommendation("a"))
        api.reports[PREVIOUS_WEEK] = serverReport(PREVIOUS_WEEK, listOf("RES_PLASTIC_BAGS" to 5))
        api.reports[olderWeek] = serverReport(olderWeek, listOf("REC_GLASS" to 2))

        assertEquals(RefreshResult.Success, repo.refreshReports())

        val list = repo.observeReports().first()
        assertEquals(listOf(LATEST_WEEK, PREVIOUS_WEEK, olderWeek), list.map { it.weekStart })
        assertEquals(listOf(true, true, false), list.map { it.hasDetails })
        assertEquals(listOf(true, false, false), list.map { it.isLatest })
        assertTrue(list.none { it.isProvisional })

        val view = repo.observeReport(LATEST_WEEK).first()!!
        assertFalse(view.isProvisional)
        assertTrue(view.canAdopt)
        assertEquals("a", view.report.hotspots.single().recommendations.single().interventionId)
        assertNull("Summary-only rows have no view", repo.observeReport(olderWeek).first())
        assertEquals(LATEST_WEEK, repo.observeLatestReport().first()!!.weekStart)
    }

    @Test
    fun `offline refresh builds a provisional report without recommendations`() = runTest {
        entries.entries = listOf(entry("RES_SACHETS", 5, LATEST_WEEK, SyncState.PENDING_CREATE))
        api.offline = true

        val result = repo.refreshReports()

        assertTrue(result is RefreshResult.Failure && result.error.isUnreachable)
        val view = repo.observeReport(LATEST_WEEK).first()!!
        assertTrue(view.isProvisional)
        assertFalse(view.canAdopt)
        assertEquals(5, view.report.stats.analyzedTotals.quantity)
        assertTrue(view.report.hotspots.single().recommendations.isEmpty())
        assertTrue(repo.observeReports().first().single().isProvisional)
    }

    @Test
    fun `offline reports never overwrite a cached server report`() = runTest {
        api.reports[LATEST_WEEK] = latestReport(recommendation("a"))
        repo.refreshReports()
        entries.entries = listOf(entry("RES_SACHETS", 99, LATEST_WEEK))
        api.offline = true

        repo.refreshReports()
        repo.refreshReport(LATEST_WEEK)

        val view = repo.observeReport(LATEST_WEEK).first()!!
        assertFalse(view.isProvisional)
        assertEquals(8, view.report.stats.analyzedTotals.quantity)
    }

    @Test
    fun `refreshAfterSync replaces the provisional report with the server one`() = runTest {
        entries.entries = listOf(entry("RES_SACHETS", 5, LATEST_WEEK, SyncState.PENDING_CREATE))
        api.offline = true
        repo.refreshReports()
        assertTrue(repo.observeReport(LATEST_WEEK).first()!!.isProvisional)

        // Back online: the outbox was flushed and the server now has the report.
        api.offline = false
        entries.entries = entries.entries.map { it.copy(syncState = SyncState.SYNCED) }
        api.reports[LATEST_WEEK] = latestReport(recommendation("a"), recommendation("b"))

        assertEquals(RefreshResult.Success, repo.refreshAfterSync())

        val view = repo.observeReport(LATEST_WEEK).first()!!
        assertFalse(view.isProvisional)
        assertEquals(2, view.report.hotspots.single().recommendations.size)
        assertTrue(api.calls.contains("GET /v1/reports/$LATEST_WEEK"))
    }

    @Test
    fun `a provisional report the server doesn't have yet stays while its entries wait to sync`() = runTest {
        entries.entries = listOf(entry("RES_SACHETS", 5, LATEST_WEEK, SyncState.PENDING_CREATE))
        api.offline = true
        repo.refreshReports()

        api.offline = false // Online, but the entry hasn't synced yet: the server has no report.
        repo.refreshReports()
        assertTrue(repo.observeReport(LATEST_WEEK).first()!!.isProvisional)

        entries.entries = entries.entries.map { it.copy(syncState = SyncState.SYNCED) }
        repo.refreshReports()
        assertNull(repo.observeReport(LATEST_WEEK).first())
    }

    @Test
    fun `a report the server no longer has is removed`() = runTest {
        api.reports[LATEST_WEEK] = latestReport()
        repo.refreshReports()
        api.reports.clear()

        assertEquals(RefreshResult.NotFound, repo.refreshReport(LATEST_WEEK))
        assertNull(cache.get(LATEST_WEEK))
    }

    @Test
    fun `a regenerated report is fetched again`() = runTest {
        api.reports[olderWeek] = serverReport(olderWeek, listOf("REC_GLASS" to 2))
        api.reports[PREVIOUS_WEEK] = serverReport(PREVIOUS_WEEK, listOf("RES_PLASTIC_BAGS" to 5))
        api.reports[LATEST_WEEK] = latestReport()
        repo.refreshReports()
        api.calls.clear()

        api.reports[PREVIOUS_WEEK] = serverReport(PREVIOUS_WEEK, listOf("RES_PLASTIC_BAGS" to 7), revision = 2)
        repo.refreshReports()

        assertTrue(api.calls.contains("GET /v1/reports/$PREVIOUS_WEEK"))
        assertEquals(7, repo.observeReport(PREVIOUS_WEEK).first()!!.report.stats.analyzedTotals.quantity)
    }

    @Test
    fun `adopt updates the cache optimistically, then keeps the server's answer`() = runTest {
        api.reports[LATEST_WEEK] = latestReport(recommendation("a"))
        repo.refreshReports()
        val gate = CompletableDeferred<Unit>()
        api.adoptionGate = gate

        val call = async { repo.setAdopted(LATEST_WEEK, "a", adopted = true) }
        yield()
        assertTrue("Optimistic", repo.observeReport(LATEST_WEEK).first()!!.adopted.any { it.interventionId == "a" })
        gate.complete(Unit)

        assertEquals(AdoptionResult.Success, call.await())
        val view = repo.observeReport(LATEST_WEEK).first()!!
        assertEquals(listOf("a"), view.adopted.map { it.interventionId })
        assertEquals(1, repo.observeReports().first().first().adoptedCount)
        assertTrue(api.calls.contains("POST adoption $LATEST_WEEK a"))
    }

    @Test
    fun `un-adopt calls DELETE`() = runTest {
        api.reports[LATEST_WEEK] = latestReport(recommendation("a", adopted = true))
        repo.refreshReports()

        assertEquals(AdoptionResult.Success, repo.setAdopted(LATEST_WEEK, "a", adopted = false))

        assertTrue(repo.observeReport(LATEST_WEEK).first()!!.adopted.isEmpty())
        assertTrue(api.calls.contains("DELETE adoption $LATEST_WEEK a"))
    }

    @Test
    fun `adopt is reverted when the request fails`() = runTest {
        api.reports[LATEST_WEEK] = latestReport(recommendation("a"))
        repo.refreshReports()
        api.offline = true

        val result = repo.setAdopted(LATEST_WEEK, "a", adopted = true)

        assertTrue(result is AdoptionResult.Failure)
        assertTrue(repo.observeReport(LATEST_WEEK).first()!!.adopted.isEmpty())
    }

    @Test
    fun `ADOPTION_WINDOW_CLOSED reverts and locks the report`() = runTest {
        api.reports[LATEST_WEEK] = latestReport(recommendation("a"))
        repo.refreshReports()
        api.adoptionError = 409 to "ADOPTION_WINDOW_CLOSED"

        assertEquals(AdoptionResult.WindowClosed, repo.setAdopted(LATEST_WEEK, "a", adopted = true))

        val view = repo.observeReport(LATEST_WEEK).first()!!
        assertTrue(view.adopted.isEmpty())
        assertFalse(view.canAdopt)
    }

    @Test
    fun `older reports and provisional reports can't be adopted, without calling the server`() = runTest {
        api.reports[PREVIOUS_WEEK] = serverReport(PREVIOUS_WEEK, listOf("RES_SACHETS" to 3), mapOf("RES_SACHETS" to listOf(recommendation("x"))))
        repo.refreshReports()
        entries.entries = listOf(entry("RES_SACHETS", 5, LATEST_WEEK))
        repo.generateOfflineReports()
        api.calls.clear()

        assertEquals(AdoptionResult.WindowClosed, repo.setAdopted(PREVIOUS_WEEK, "x", adopted = true))
        assertEquals(AdoptionResult.NotAvailable, repo.setAdopted(LATEST_WEEK, "x", adopted = true))
        assertEquals(AdoptionResult.NotAvailable, repo.setAdopted(PREVIOUS_WEEK, "unknown", adopted = true))
        assertTrue(api.calls.isEmpty())
        assertFalse(repo.observeReport(PREVIOUS_WEEK).first()!!.canAdopt)
    }

    @Test
    fun `adoptions are 'not measured' once the following week closed with no report`() = runTest {
        api.reports[LATEST_WEEK] = latestReport(recommendation("a", adopted = true))
        repo.refreshReports()
        assertFalse("Following week is still open", repo.observeReport(LATEST_WEEK).first()!!.followupNotMeasured)

        // Two weeks later: the week after LATEST_WEEK closed and nothing was logged, so there's no report for it.
        val later = repository(Clock.fixed(NOW.plusSeconds(7L * 24 * 3600), ZoneOffset.UTC))
        later.refreshReports()

        val view = later.observeReport(LATEST_WEEK).first()!!
        assertTrue(view.followupNotMeasured)
        assertFalse(view.canAdopt)
    }

    @Test
    fun `app-open offline trigger builds the latest and the one before`() = runTest {
        entries.entries = listOf(
            entry("RES_SACHETS", 5, LATEST_WEEK),
            entry("REC_GLASS", 1, PREVIOUS_WEEK),
            entry("REC_GLASS", 1, LocalDate.of(2026, 10, 4)), // Current week: no report.
        )

        assertEquals(setOf(LATEST_WEEK, PREVIOUS_WEEK), repo.generateOfflineReports().toSet())
        assertNotNull(repo.observeReport(PREVIOUS_WEEK).first())
        // The comparison of W uses local W−1 entries (the provisional W−1 is not a server report).
        assertEquals(1, repo.observeReport(LATEST_WEEK).first()!!.report.comparison!!.total.previous)
    }
}
