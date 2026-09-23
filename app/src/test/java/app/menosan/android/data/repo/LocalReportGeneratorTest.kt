package app.menosan.android.data.repo

import app.menosan.android.core.analytics.ALGORITHM_VERSION
import app.menosan.android.data.local.SyncState
import app.menosan.android.data.remote.dto.HotspotCriterion
import app.menosan.android.data.remote.dto.Trend
import app.menosan.android.data.repo.ReportFixtures.CLOCK
import app.menosan.android.data.repo.ReportFixtures.CURRENT_WEEK
import app.menosan.android.data.repo.ReportFixtures.LATEST_WEEK
import app.menosan.android.data.repo.ReportFixtures.NOW
import app.menosan.android.data.repo.ReportFixtures.PREVIOUS_WEEK
import app.menosan.android.data.repo.ReportFixtures.TAXONOMY
import app.menosan.android.data.repo.ReportFixtures.entry
import app.menosan.android.data.repo.ReportFixtures.recommendation
import app.menosan.android.data.repo.ReportFixtures.serverReport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Plan §5.7: the offline provisional report built from Room. */
class LocalReportGeneratorTest {
    private val entries = FakeEntrySource()
    private val cache = FakeReportCacheDao()
    private val generator = LocalReportGenerator(entries, cache, { TAXONOMY }, CLOCK)

    private val weekEntries = listOf(
        entry("RES_SACHETS", 5, LATEST_WEEK, SyncState.PENDING_CREATE),
        entry("RES_SACHETS", 3, LATEST_WEEK),
        entry("RES_PLASTIC_BAGS", 4, LATEST_WEEK),
        entry("SPC_BATTERIES", 2, LATEST_WEEK),
    )

    private fun build(
        previousEntries: List<app.menosan.android.data.local.EntryEntity> = emptyList(),
        previousRow: app.menosan.android.data.local.ReportCacheEntity? = null,
    ) = LocalReportGenerator.build(LATEST_WEEK, weekEntries, previousEntries, previousRow, TAXONOMY, NOW, LATEST_WEEK)

    @Test
    fun `totals, special line, and hotspots come from local entries, including unsynced ones`() {
        val payload = build()
        val report = payload.report

        assertEquals(LATEST_WEEK, report.weekStart)
        assertEquals(LocalDate.of(2026, 10, 3), report.weekEnd)
        assertEquals(0, report.revision)
        assertTrue(report.isLatest)
        assertEquals(3, report.stats.analyzedTotals.frequency)
        assertEquals(12, report.stats.analyzedTotals.quantity)
        assertEquals(2, report.stats.special.quantity)
        assertEquals("RES_SACHETS", report.hotspots.first().subcategory)
        assertEquals(
            listOf(HotspotCriterion.MOST_FREQUENT, HotspotCriterion.HIGHEST_QUANTITY, HotspotCriterion.AVOIDABLE),
            report.hotspots.first().criteria,
        )
        assertTrue("No recommendations offline", report.hotspots.all { it.recommendations.isEmpty() })
        assertEquals(ALGORITHM_VERSION, payload.algorithmVersion)
        assertEquals(NOW, payload.generatedAt)
    }

    @Test
    fun `nothing about the previous week means no comparison, impact, or recap`() {
        val payload = build()

        assertNull(payload.report.comparison)
        assertTrue(payload.report.impacts.isEmpty())
        assertNull(payload.recap)
        assertEquals(ComparisonSource.NONE, payload.comparisonSource)
        assertFalse(payload.missingComparisons)
    }

    @Test
    fun `cached server W-1 report gives the comparison, impact with titles, and the recap`() {
        val previous = serverReport(
            PREVIOUS_WEEK,
            hotspots = listOf("RES_SACHETS" to 10, "RES_PLASTIC_BAGS" to 4, "REC_PET_BOTTLES" to 2),
            recommendations = mapOf(
                "RES_SACHETS" to listOf(recommendation("a", adopted = true, title = "Refill station"), recommendation("b")),
                "RES_PLASTIC_BAGS" to listOf(recommendation("c", adopted = true, title = "Bring a bayong")),
            ),
            isLatest = false,
        )
        // Local W−1 entries disagree on purpose: the server's stats win.
        val payload = build(
            previousEntries = listOf(entry("RES_SACHETS", 1, PREVIOUS_WEEK)),
            previousRow = previous.toCacheEntity(NOW),
        )

        val comparison = payload.report.comparison!!
        assertEquals(ComparisonSource.SERVER_REPORT, payload.comparisonSource)
        assertEquals(PREVIOUS_WEEK, comparison.previousWeekStart)
        assertEquals(16, comparison.total.previous)
        assertEquals(12, comparison.total.current)
        assertEquals(Trend.DECREASED, comparison.total.trend)

        val impacts = payload.report.impacts
        assertEquals(listOf("RES_PLASTIC_BAGS", "RES_SACHETS"), impacts.map { it.targetSubcategory })
        val bags = impacts.first()
        assertEquals("Bring a bayong", bags.title)
        assertEquals(4, bags.baselineQuantity)
        assertEquals(4, bags.followupQuantity)
        assertEquals(Trend.SAME, bags.result)
        assertEquals(PREVIOUS_WEEK, bags.baselineWeekStart)
        val sachets = impacts.last()
        assertEquals(10, sachets.baselineQuantity)
        assertEquals(8, sachets.followupQuantity)
        assertEquals(Trend.DECREASED, sachets.result)

        val recap = payload.recap!!
        assertEquals(PREVIOUS_WEEK, recap.weekStart)
        assertEquals(listOf("RES_SACHETS", "RES_PLASTIC_BAGS", "REC_PET_BOTTLES"), recap.hotspots.map { it.subcategory })
        assertEquals(setOf("Refill station", "Bring a bayong"), recap.adopted.map { it.title }.toSet())
        assertFalse(payload.missingComparisons)
    }

    @Test
    fun `local W-1 entries give the comparison when no server report is cached`() {
        val payload = build(previousEntries = listOf(entry("RES_SACHETS", 20, PREVIOUS_WEEK)))

        assertEquals(ComparisonSource.LOCAL_ENTRIES, payload.comparisonSource)
        assertEquals(20, payload.report.comparison!!.total.previous)
        assertEquals(Trend.DECREASED, payload.report.comparison!!.total.trend)
        assertTrue(payload.report.impacts.isEmpty())
        assertNull(payload.recap)
    }

    @Test
    fun `a W-1 summary without details flags the missing comparison and impact`() {
        val summaryOnly = ReportFixtures.summary(serverReport(PREVIOUS_WEEK, listOf("RES_SACHETS" to 9)))
            .copy(adoptedCount = 1)
            .toCacheEntity(NOW)

        val payload = build(previousRow = summaryOnly)

        assertNull(payload.report.comparison)
        assertTrue(payload.missingComparisons)
    }

    @Test
    fun `only special waste gives a report with no hotspots`() {
        val payload = LocalReportGenerator.build(
            LATEST_WEEK, listOf(entry("SPC_MEDICAL", 1, LATEST_WEEK)), emptyList(), null, TAXONOMY, NOW, LATEST_WEEK,
        )
        assertTrue(payload.report.hotspots.isEmpty())
        assertEquals(1, payload.report.stats.special.frequency)
        assertEquals(0, payload.report.stats.analyzedTotals.quantity)
    }

    @Test
    fun `generate stores a provisional row for a closed week with local entries`() = runTest {
        entries.entries = weekEntries

        assertEquals(listOf(LATEST_WEEK), generator.generateMissing())

        val row = cache.get(LATEST_WEEK)!!
        assertTrue(row.isProvisional)
        assertEquals(ALGORITHM_VERSION, row.algorithmVersion)
        assertNull(row.revision)
        assertEquals(12, row.analyzedQuantity)
        assertEquals(NOW, row.fetchedAt)
        assertEquals(12, row.provisionalPayload()!!.report.stats.analyzedTotals.quantity)
    }

    @Test
    fun `generate skips open weeks, empty weeks, and weeks with a cached server report`() = runTest {
        entries.entries = weekEntries + entry("RES_SACHETS", 1, CURRENT_WEEK)

        assertNull("Current week is still open", generator.generate(CURRENT_WEEK))
        assertNull("Nothing logged", generator.generate(PREVIOUS_WEEK))

        cache.upsert(serverReport(LATEST_WEEK, listOf("RES_SACHETS" to 8)).toCacheEntity(NOW))
        assertNull("Server report cached", generator.generate(LATEST_WEEK))
        assertFalse(cache.get(LATEST_WEEK)!!.isProvisional)
    }

    @Test
    fun `generate replaces a summary-only server row, since it has no details to show`() = runTest {
        entries.entries = weekEntries
        cache.upsert(ReportFixtures.summary(serverReport(LATEST_WEEK, listOf("RES_SACHETS" to 8))).toCacheEntity(NOW))

        assertNotNull(generator.generate(LATEST_WEEK))
        assertTrue(cache.get(LATEST_WEEK)!!.isProvisional)
    }
}
