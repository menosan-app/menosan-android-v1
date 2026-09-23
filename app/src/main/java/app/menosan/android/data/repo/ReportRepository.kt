package app.menosan.android.data.repo

import app.menosan.android.core.network.ApiError
import app.menosan.android.data.remote.dto.RecommendationDto
import app.menosan.android.data.remote.dto.ReportDto
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Weekly reports (plan §5.5–§5.7, AN-3). Room's `reports_cache` is what the UI observes; the network only refreshes it,
 * so cached reports stay viewable offline. When a week has closed, its server report isn't cached, and the API can't be
 * reached, a provisional offline report is built on the device ([LocalReportGenerator]) until the server's replaces it.
 */
interface ReportRepository {
    /** Every cached report, newest first: server summaries, full server reports, and provisional ones. */
    fun observeReports(): Flow<List<ReportListItem>>

    /** One week's report, or null while nothing is cached for it (a summary-only row is null too). */
    fun observeReport(weekStart: LocalDate): Flow<ReportView?>

    /** The newest report with details (server or provisional), for Home. Null when there is none yet. */
    fun observeLatestReport(): Flow<ReportView?>

    /**
     * `GET /v1/reports`, then the full reports that are missing, provisional, outdated, or the latest one.
     * If the API can't be reached, builds provisional reports for closed weeks that have local entries instead.
     */
    suspend fun refreshReports(): RefreshResult

    /** `GET /v1/reports/{weekStart}`. Falls back to a provisional report when the API can't be reached. */
    suspend fun refreshReport(weekStart: LocalDate): RefreshResult

    /**
     * Adopts or un-adopts one recommendation (plan §6.3), only while the report is the latest one (I7).
     * The cache is updated optimistically and reverted if the server says no.
     */
    suspend fun setAdopted(weekStart: LocalDate, interventionId: String, adopted: Boolean): AdoptionResult

    /**
     * Call from `SyncWorker` after the outbox was flushed: fetches server reports for closed weeks whose report is
     * provisional or missing, and replaces provisional ones (plan §5.7 "Replacement").
     */
    suspend fun refreshAfterSync(): RefreshResult

    /** App-open trigger for plan §5.7 while offline: builds provisional reports where needed. Returns their weeks. */
    suspend fun generateOfflineReports(): List<LocalDate>
}

/** One row of the Insights list. */
data class ReportListItem(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val analyzedQuantity: Int,
    val hotspotCount: Int,
    val adoptedCount: Int,
    /** The report that still accepts adoptions (plan I7), from the device clock. */
    val isLatest: Boolean,
    /** An offline summary built on this device (plan §5.7). */
    val isProvisional: Boolean,
    /** The full report is cached, so it opens offline. */
    val hasDetails: Boolean,
)

/** A report ready for the UI: the server payload, or a provisional one without recommendations. */
data class ReportView(
    val report: ReportDto,
    val isProvisional: Boolean,
    /** Adopt / Un-adopt is allowed: a server report that is the latest one (plan I7). */
    val canAdopt: Boolean,
    /** Provisional only: last week's hotspots and adopted interventions, from the cached W−1 report. */
    val recap: LastWeekRecap?,
    /** Provisional only: the comparison or last week's impact couldn't be computed from local data. */
    val missingComparisons: Boolean,
    /**
     * Interventions adopted on this report weren't measured, because the following week closed with nothing logged
     * (plan §5.4 "Not measured").
     */
    val followupNotMeasured: Boolean,
    /** When the server report was fetched, or when the provisional one was built. */
    val savedAt: Instant,
) {
    val weekStart: LocalDate get() = report.weekStart
    val weekEnd: LocalDate get() = report.weekEnd

    /** Interventions adopted on this report ("This week you're trying: …" on Home when it's the latest). */
    val adopted: List<RecommendationDto> get() = report.hotspots.flatMap { it.recommendations }.filter { it.adopted }
}

sealed interface RefreshResult {
    data object Success : RefreshResult

    /** The server has no report for that week (still open, or nothing was logged). */
    data object NotFound : RefreshResult

    data class Failure(val error: ApiError) : RefreshResult
}

sealed interface AdoptionResult {
    data object Success : AdoptionResult

    /** The report is no longer the latest one, so adoptions are locked (plan I7, `ADOPTION_WINDOW_CLOSED`). */
    data object WindowClosed : AdoptionResult

    /** Nothing to adopt: no cached server report, a provisional one, or an unknown intervention. */
    data object NotAvailable : AdoptionResult

    data class Failure(val error: ApiError) : AdoptionResult
}

/** No connection, a timeout, or the server (or a proxy in front of it) is down: time for the offline fallback. */
val ApiError.isUnreachable: Boolean
    get() = when (this) {
        is ApiError.Network -> true
        is ApiError.Http -> status >= 500
        is ApiError.Unexpected -> false
    }
