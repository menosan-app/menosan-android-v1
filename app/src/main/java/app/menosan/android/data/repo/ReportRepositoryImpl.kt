package app.menosan.android.data.repo

import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.network.ApiResult
import app.menosan.android.core.network.isError
import app.menosan.android.core.network.safeApiCall
import app.menosan.android.core.time.WeekCalc
import app.menosan.android.data.local.ReportCacheDao
import app.menosan.android.data.local.ReportCacheEntity
import app.menosan.android.data.remote.MenosanApi
import app.menosan.android.data.remote.dto.AdoptRequest
import app.menosan.android.data.remote.dto.ReportDto
import app.menosan.android.data.remote.dto.ReportSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ReportRepository] over `reports_cache` (Room) and contract §5.7/§5.9. Every write to the cache goes through [mutex],
 * so a provisional report never overwrites a server report that was stored a moment earlier.
 */
@Singleton
class DefaultReportRepository @Inject constructor(
    private val api: MenosanApi,
    private val cacheDao: ReportCacheDao,
    private val entries: ReportEntrySource,
    private val generator: LocalReportGenerator,
    private val clock: Clock,
) : ReportRepository {
    private val mutex = Mutex()

    override fun observeReports(): Flow<List<ReportListItem>> =
        cacheDao.observeAll()
            .map { rows -> rows.map { it.toListItem() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun observeReport(weekStart: LocalDate): Flow<ReportView?> =
        cacheDao.observeAll()
            .map { rows -> rows.firstOrNull { it.weekStart == weekStart }?.toView(rows) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun observeLatestReport(): Flow<ReportView?> =
        cacheDao.observeAll()
            .map { rows -> rows.firstNotNullOfOrNull { it.toView(rows) } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override suspend fun refreshReports(): RefreshResult {
        val list = when (val result = safeApiCall { api.reports() }) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> {
                if (result.error.isUnreachable) generateOfflineReports()
                return RefreshResult.Failure(result.error)
            }
        }
        val toFetch = mutex.withLock { syncList(list) }
        for (week in toFetch) {
            val result = fetchReport(week)
            if (result is RefreshResult.Failure && result.error.isUnreachable) {
                generateOfflineReports()
                break
            }
        }
        return RefreshResult.Success
    }

    override suspend fun refreshReport(weekStart: LocalDate): RefreshResult {
        val result = fetchReport(weekStart)
        if (result is RefreshResult.Failure && result.error.isUnreachable) {
            mutex.withLock { generator.generate(weekStart) }
        }
        return result
    }

    override suspend fun refreshAfterSync(): RefreshResult = refreshReports()

    override suspend fun generateOfflineReports(): List<LocalDate> = mutex.withLock { generator.generateMissing() }

    override suspend fun setAdopted(weekStart: LocalDate, interventionId: String, adopted: Boolean): AdoptionResult {
        val report = mutex.withLock { cacheDao.get(weekStart)?.serverReport() } ?: return AdoptionResult.NotAvailable
        val recommendation = report.hotspots.flatMap { it.recommendations }.firstOrNull { it.interventionId == interventionId }
            ?: return AdoptionResult.NotAvailable
        if (!canAdopt(report)) return AdoptionResult.WindowClosed
        if (recommendation.adopted == adopted) return AdoptionResult.Success

        mutex.withLock { applyAdopted(weekStart, interventionId, adopted) } // Optimistic.
        val result = if (adopted) {
            safeApiCall { api.adopt(weekStart.toString(), AdoptRequest(listOf(interventionId))) }
        } else {
            safeApiCall { api.unadopt(weekStart.toString(), interventionId) }
        }
        return when (result) {
            is ApiResult.Success -> {
                mutex.withLock { cacheDao.upsert(result.value.toCacheEntity(clock.instant())) }
                AdoptionResult.Success
            }
            is ApiResult.Failure -> {
                val windowClosed = result.isError(ApiErrorCode.ADOPTION_WINDOW_CLOSED)
                mutex.withLock { applyAdopted(weekStart, interventionId, !adopted, closeWindow = windowClosed) } // Revert.
                if (windowClosed) AdoptionResult.WindowClosed else AdoptionResult.Failure(result.error)
            }
        }
    }

    // ---- internals -------------------------------------------------------------------------------------------------

    /**
     * Stores the `GET /v1/reports` rows (caller holds [mutex]) and returns the weeks whose full report should be
     * fetched: provisional ones, the newest [KEEP_FULL_REPORTS] (retention, plan §5.7), the latest one (adoption flags
     * may have changed elsewhere), and any whose summary changed (regenerated, plan §5.6).
     */
    private suspend fun syncList(list: List<ReportSummaryDto>): List<LocalDate> {
        val now = clock.instant()
        val cached = cacheDao.getAll().associateBy { it.weekStart }
        val listed = list.map { it.weekStart }.toSet()
        val pendingWeeks = pendingWeeks()

        // Reports the server doesn't have. A provisional one stays while its week still has entries waiting to sync.
        cached.values
            .filter { it.weekStart !in listed && !(it.isProvisional && it.weekStart in pendingWeeks) }
            .forEach { cacheDao.delete(it.weekStart) }

        val toFetch = mutableListOf<LocalDate>()
        val rows = list.mapIndexedNotNull { index, summary ->
            val row = cached[summary.weekStart]
            val keepDetails = index < KEEP_FULL_REPORTS
            when {
                // Keep the provisional report on screen until the server's full report replaces it.
                row?.isProvisional == true -> {
                    toFetch += summary.weekStart
                    null
                }
                row == null || row.serverReport() == null -> {
                    if (keepDetails || summary.isLatest) toFetch += summary.weekStart
                    summary.toCacheEntity(now)
                }
                else -> {
                    if (summary.isLatest || row.differsFrom(summary)) toFetch += summary.weekStart
                    row.copy(isLatest = summary.isLatest, fetchedAt = now)
                }
            }
        }
        cacheDao.upsertAll(rows)
        return toFetch.distinct()
    }

    private suspend fun fetchReport(weekStart: LocalDate): RefreshResult =
        when (val result = safeApiCall { api.report(weekStart.toString()) }) {
            is ApiResult.Success -> {
                mutex.withLock { cacheDao.upsert(result.value.toCacheEntity(clock.instant())) }
                RefreshResult.Success
            }
            is ApiResult.Failure -> if (result.isError(ApiErrorCode.NOT_FOUND)) {
                mutex.withLock { removeIfGone(weekStart) }
                RefreshResult.NotFound
            } else {
                RefreshResult.Failure(result.error)
            }
        }

    /** The server has no report for [weekStart]. Keep a provisional one only while that week has entries to sync. */
    private suspend fun removeIfGone(weekStart: LocalDate) {
        val row = cacheDao.get(weekStart) ?: return
        if (!row.isProvisional || weekStart !in pendingWeeks()) cacheDao.delete(weekStart)
    }

    private suspend fun pendingWeeks(): Set<LocalDate> = entries.weeksWithPendingEntries()

    /** Sets one recommendation's `adopted` flag in the cached report (caller holds [mutex]). */
    private suspend fun applyAdopted(weekStart: LocalDate, interventionId: String, adopted: Boolean, closeWindow: Boolean = false) {
        val row = cacheDao.get(weekStart) ?: return
        val report = row.serverReport() ?: return
        val updated = report.copy(
            isLatest = report.isLatest && !closeWindow,
            hotspots = report.hotspots.map { hotspot ->
                hotspot.copy(
                    recommendations = hotspot.recommendations.map {
                        if (it.interventionId == interventionId) it.copy(adopted = adopted) else it
                    },
                )
            },
        )
        cacheDao.upsert(updated.toCacheEntity(row.fetchedAt))
    }

    private fun canAdopt(report: ReportDto): Boolean =
        report.isLatest && report.weekStart == WeekCalc.latestReportWeekStart(clock)

    private fun ReportCacheEntity.toListItem() = ReportListItem(
        weekStart = weekStart,
        weekEnd = weekEnd,
        analyzedQuantity = analyzedQuantity,
        hotspotCount = hotspotCount,
        adoptedCount = adoptedCount,
        isLatest = weekStart == WeekCalc.latestReportWeekStart(clock),
        isProvisional = isProvisional,
        hasDetails = payloadJson != null,
    )

    private fun ReportCacheEntity.toView(rows: List<ReportCacheEntity>): ReportView? {
        serverReport()?.let { report ->
            // Plan §5.4: nothing was logged the following week, so there is no report to measure adoptions in.
            // Only claimed once the report list was refreshed after that week closed.
            val followupWeek = weekStart.plusDays(7)
            val followupNotMeasured = report.adoptedCount() > 0 &&
                WeekCalc.isClosed(followupWeek, clock) &&
                !fetchedAt.isBefore(WeekCalc.endExclusive(followupWeek)) &&
                rows.none { it.weekStart == followupWeek }
            return ReportView(
                report = report,
                isProvisional = false,
                canAdopt = canAdopt(report),
                recap = null,
                missingComparisons = false,
                followupNotMeasured = followupNotMeasured,
                savedAt = fetchedAt,
            )
        }
        return provisionalPayload()?.let {
            ReportView(
                report = it.report,
                isProvisional = true,
                canAdopt = false,
                recap = it.recap,
                missingComparisons = it.missingComparisons,
                followupNotMeasured = false,
                savedAt = it.generatedAt,
            )
        }
    }

    companion object {
        /** Full server reports kept for offline viewing: at least the last 2 (plan §5.7 retention). */
        const val KEEP_FULL_REPORTS = 2
    }
}
