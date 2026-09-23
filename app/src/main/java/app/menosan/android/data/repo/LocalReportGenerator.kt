package app.menosan.android.data.repo

import app.menosan.android.core.analytics.AdoptionInput
import app.menosan.android.core.analytics.EntryInput
import app.menosan.android.core.analytics.SubcategoryInfo
import app.menosan.android.core.analytics.WeeklyStats
import app.menosan.android.core.analytics.aggregate
import app.menosan.android.core.analytics.analyticsTaxonomy
import app.menosan.android.core.analytics.compare
import app.menosan.android.core.analytics.findHotspots
import app.menosan.android.core.analytics.measureImpact
import app.menosan.android.core.model.Taxonomy
import app.menosan.android.core.time.WeekCalc
import app.menosan.android.data.local.EntryDao
import app.menosan.android.data.local.EntryEntity
import app.menosan.android.data.local.ReportCacheDao
import app.menosan.android.data.local.ReportCacheEntity
import app.menosan.android.data.remote.dto.ImpactDto
import app.menosan.android.data.remote.dto.ReportDto
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Builds the offline provisional report of plan §5.7 (I12) from Room, with the analytics port in `core/analytics`:
 *
 * - totals, breakdowns, the Special line, and hotspots from local entries of week W (unsynced ones included);
 * - the comparison with W−1 from the cached **server** W−1 report's stats, or else from local W−1 entries;
 * - the impact of interventions adopted on the cached W−1 report, measured against local W entries;
 * - a recap of the W−1 report (its hotspots and adopted interventions).
 *
 * It never adds recommendations or adoption; those are online-only. Stored in `reports_cache` with
 * `is_provisional = true`, and replaced by the server report once it can be fetched.
 * The caller serializes access to `reports_cache` (see [DefaultReportRepository]).
 */
class LocalReportGenerator internal constructor(
    private val entries: ReportEntrySource,
    private val cacheDao: ReportCacheDao,
    private val taxonomy: suspend () -> Taxonomy,
    private val clock: Clock,
) {
    @Inject
    constructor(entries: ReportEntrySource, cacheDao: ReportCacheDao, taxonomyRepository: TaxonomyRepository, clock: Clock) :
        this(entries, cacheDao, { taxonomyRepository.taxonomy() }, clock)

    /**
     * Builds provisional reports for the closed weeks Room still holds entries for (the two weeks before the current
     * one, plan §5.7 retention) that have no cached server report. Returns the weeks it (re)built.
     */
    suspend fun generateMissing(): List<LocalDate> {
        val latest = WeekCalc.latestReportWeekStart(clock)
        return listOf(latest, latest.minusDays(7)).filter { generate(it) != null }
    }

    /**
     * (Re)builds the provisional report for [weekStart] and stores it. Returns null, and stores nothing, when the week
     * isn't closed, has no local entries, or already has a cached server report.
     */
    suspend fun generate(weekStart: LocalDate): ProvisionalReportPayload? {
        if (!WeekCalc.isClosed(weekStart, clock)) return null
        val cached = cacheDao.get(weekStart)
        if (cached != null && cached.serverReport() != null) return null
        val weekEntries = entries.entriesOf(weekStart)
        if (weekEntries.isEmpty()) return null

        val previousWeek = weekStart.minusDays(7)
        val previousRow = cacheDao.get(previousWeek)
        val payload = build(
            weekStart = weekStart,
            entries = weekEntries,
            previousEntries = entries.entriesOf(previousWeek),
            previousRow = previousRow,
            taxonomy = taxonomy(),
            now = clock.instant(),
            latestReportWeekStart = WeekCalc.latestReportWeekStart(clock),
        )
        cacheDao.upsert(payload.toCacheEntity())
        return payload
    }

    companion object {
        /** Pure part of [generate], for tests. [previousRow] is whatever `reports_cache` holds for W−1 (may be null). */
        internal fun build(
            weekStart: LocalDate,
            entries: List<EntryEntity>,
            previousEntries: List<EntryEntity>,
            previousRow: ReportCacheEntity?,
            taxonomy: Taxonomy,
            now: Instant,
            latestReportWeekStart: LocalDate,
        ): ProvisionalReportPayload {
            val tax = taxonomy.analyticsTaxonomy()
            val stats = aggregate(entries.toInputs(tax), tax)
            val hotspots = findHotspots(stats, tax)

            val previousWeek = weekStart.minusDays(7)
            val previousReport = previousRow?.serverReport()
            val localPrevious = previousEntries.toInputs(tax).takeIf { it.isNotEmpty() }?.let { aggregate(it, tax) }
            val (previousStats: WeeklyStats?, source) = when {
                previousReport != null -> previousReport.stats.toAnalytics() to ComparisonSource.SERVER_REPORT
                localPrevious != null -> localPrevious to ComparisonSource.LOCAL_ENTRIES
                else -> null to ComparisonSource.NONE
            }
            val comparison = compare(stats, previousStats, previousWeek.toString())

            // Adoptions made on report W−1, with the baseline stored at adoption time (the hotspot's quantity).
            val adopted = previousReport?.hotspots.orEmpty().flatMap { hotspot ->
                hotspot.recommendations.filter { it.adopted }.map { hotspot to it }
            }.distinctBy { (_, rec) -> rec.interventionId }
            val titles = adopted.associate { (_, rec) -> rec.interventionId to rec.title }
            val impacts = measureImpact(
                adopted.map { (hotspot, rec) -> AdoptionInput(rec.interventionId, hotspot.subcategory, hotspot.quantity) },
                stats,
            ).map {
                ImpactDto(
                    interventionId = it.interventionId,
                    title = titles.getValue(it.interventionId),
                    targetSubcategory = it.targetSubcategory,
                    baselineWeekStart = previousWeek,
                    baselineQuantity = it.baselineQuantity,
                    followupQuantity = it.followupQuantity,
                    result = it.result.toDto(),
                )
            }

            val recap = previousReport?.let { prev ->
                LastWeekRecap(
                    weekStart = prev.weekStart,
                    weekEnd = prev.weekEnd,
                    hotspots = prev.hotspots.sortedBy { it.rank }.map { RecapHotspot(it.subcategory, it.frequency, it.quantity) },
                    adopted = adopted.map { (hotspot, rec) -> RecapAdoption(rec.interventionId, rec.title, hotspot.subcategory) },
                )
            }

            // The server knows W−1 had analyzed data or adoptions, but its report isn't cached here.
            val serverSummary = previousRow?.takeIf { !it.isProvisional && previousReport == null }
            val comparisonMissing = comparison == null && (serverSummary?.analyzedQuantity ?: 0) > 0
            val impactMissing = (serverSummary?.adoptedCount ?: 0) > 0

            return ProvisionalReportPayload(
                report = ReportDto(
                    weekStart = weekStart,
                    weekEnd = WeekCalc.weekEnd(weekStart),
                    revision = 0,
                    isLatest = weekStart == latestReportWeekStart,
                    stats = stats.toDto(),
                    hotspots = hotspots.map { it.toDto() },
                    comparison = comparison?.toDto(),
                    impacts = impacts,
                ),
                recap = recap,
                comparisonSource = if (comparison == null) ComparisonSource.NONE else source,
                missingComparisons = comparisonMissing || impactMissing,
                generatedAt = now,
            )
        }

        /** Entries whose subcategory this taxonomy doesn't know are skipped rather than failing the whole report. */
        private fun List<EntryEntity>.toInputs(tax: Map<String, SubcategoryInfo>): List<EntryInput> =
            filter { it.subcategory in tax }.map { EntryInput(it.subcategory, it.quantity) }
    }
}

/**
 * The entry reads that reports need, over AN-1's `entries` table. Kept narrow so AN-1 can extend `EntryDao` freely
 * and AN-3's tests don't have to fake the whole DAO.
 */
interface ReportEntrySource {
    /** Entries of one logging week, unsynced ones included (not those waiting to be deleted). */
    suspend fun entriesOf(weekStart: LocalDate): List<EntryEntity>

    /** Weeks that still have entries waiting to sync. */
    suspend fun weeksWithPendingEntries(): Set<LocalDate>
}

class RoomReportEntrySource @Inject constructor(private val dao: EntryDao) : ReportEntrySource {
    override suspend fun entriesOf(weekStart: LocalDate): List<EntryEntity> = dao.getWeek(weekStart)

    override suspend fun weeksWithPendingEntries(): Set<LocalDate> = dao.getPending().map { it.weekStart }.toSet()
}
