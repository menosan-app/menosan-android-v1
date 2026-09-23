package app.menosan.android.data.repo

import app.menosan.android.core.analytics.ALGORITHM_VERSION
import app.menosan.android.core.analytics.CategoryStats
import app.menosan.android.core.analytics.Comparison
import app.menosan.android.core.analytics.Hotspot
import app.menosan.android.core.analytics.SubcategoryStats
import app.menosan.android.core.analytics.Totals
import app.menosan.android.core.analytics.WeeklyStats
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.network.IsoDate
import app.menosan.android.core.network.IsoInstant
import app.menosan.android.core.network.MenosanJson
import app.menosan.android.data.local.ReportCacheEntity
import app.menosan.android.data.remote.dto.CategoryComparisonDto
import app.menosan.android.data.remote.dto.CategoryStatsDto
import app.menosan.android.data.remote.dto.ComparisonDto
import app.menosan.android.data.remote.dto.ComparisonRowDto
import app.menosan.android.data.remote.dto.HotspotCriterion
import app.menosan.android.data.remote.dto.HotspotDto
import app.menosan.android.data.remote.dto.ReportDto
import app.menosan.android.data.remote.dto.ReportSummaryDto
import app.menosan.android.data.remote.dto.SubcategoryComparisonDto
import app.menosan.android.data.remote.dto.SubcategoryStatsDto
import app.menosan.android.data.remote.dto.TotalsDto
import app.menosan.android.data.remote.dto.Trend
import app.menosan.android.data.remote.dto.WeeklyStatsDto
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import app.menosan.android.core.analytics.HotspotCriterion as AnalyticsCriterion
import app.menosan.android.core.analytics.Trend as AnalyticsTrend

// Cache formats of `reports_cache.payload_json` (AN-3) and conversions between the analytics port and the DTOs.

/** Recap of the previous week's report inside a provisional report (plan §5.7). */
@Serializable
data class LastWeekRecap(
    val weekStart: IsoDate,
    val weekEnd: IsoDate,
    val hotspots: List<RecapHotspot>,
    val adopted: List<RecapAdoption>,
)

@Serializable
data class RecapHotspot(val subcategory: String, val frequency: Int, val quantity: Int)

@Serializable
data class RecapAdoption(val interventionId: String, val title: String, val targetSubcategory: String)

/** Where a provisional report's comparison with W−1 came from. */
@Serializable
enum class ComparisonSource { SERVER_REPORT, LOCAL_ENTRIES, NONE }

/**
 * `payload_json` of a provisional row (`is_provisional = true`). [report] has the server's shape so the same UI can
 * show it, with no recommendations and `revision = 0`.
 */
@Serializable
data class ProvisionalReportPayload(
    val report: ReportDto,
    val recap: LastWeekRecap? = null,
    val comparisonSource: ComparisonSource = ComparisonSource.NONE,
    val missingComparisons: Boolean = false,
    val algorithmVersion: Int = ALGORITHM_VERSION,
    val generatedAt: IsoInstant,
)

// ---- cache rows ------------------------------------------------------------------------------------------------

internal fun ReportDto.adoptedCount(): Int = hotspots.sumOf { h -> h.recommendations.count { it.adopted } }

/** A full server report row. Summary columns are derived from the payload. */
internal fun ReportDto.toCacheEntity(fetchedAt: Instant): ReportCacheEntity = ReportCacheEntity(
    weekStart = weekStart,
    weekEnd = weekEnd,
    analyzedQuantity = stats.analyzedTotals.quantity,
    hotspotCount = hotspots.size,
    adoptedCount = adoptedCount(),
    isLatest = isLatest,
    isProvisional = false,
    revision = revision,
    algorithmVersion = null,
    payloadJson = MenosanJson.encodeToString(ReportDto.serializer(), this),
    fetchedAt = fetchedAt,
)

/** A `GET /v1/reports` row without details. */
internal fun ReportSummaryDto.toCacheEntity(fetchedAt: Instant): ReportCacheEntity = ReportCacheEntity(
    weekStart = weekStart,
    weekEnd = weekEnd,
    analyzedQuantity = analyzedQuantity,
    hotspotCount = hotspotCount,
    adoptedCount = adoptedCount,
    isLatest = isLatest,
    isProvisional = false,
    revision = null,
    algorithmVersion = null,
    payloadJson = null,
    fetchedAt = fetchedAt,
)

internal fun ProvisionalReportPayload.toCacheEntity(): ReportCacheEntity = ReportCacheEntity(
    weekStart = report.weekStart,
    weekEnd = report.weekEnd,
    analyzedQuantity = report.stats.analyzedTotals.quantity,
    hotspotCount = report.hotspots.size,
    adoptedCount = 0,
    isLatest = report.isLatest,
    isProvisional = true,
    revision = null,
    algorithmVersion = algorithmVersion,
    payloadJson = MenosanJson.encodeToString(ProvisionalReportPayload.serializer(), this),
    fetchedAt = generatedAt,
)

/** The full server report in a row, or null for a summary-only, provisional, or unreadable row. */
internal fun ReportCacheEntity.serverReport(): ReportDto? {
    if (isProvisional) return null
    val json = payloadJson ?: return null
    return runCatching { MenosanJson.decodeFromString(ReportDto.serializer(), json) }.getOrNull()
}

internal fun ReportCacheEntity.provisionalPayload(): ProvisionalReportPayload? {
    if (!isProvisional) return null
    val json = payloadJson ?: return null
    return runCatching { MenosanJson.decodeFromString(ProvisionalReportPayload.serializer(), json) }.getOrNull()
}

/** A full server report row whose summary columns disagree with the server's list (e.g. regenerated, §5.6). */
internal fun ReportCacheEntity.differsFrom(summary: ReportSummaryDto): Boolean =
    analyzedQuantity != summary.analyzedQuantity || hotspotCount != summary.hotspotCount ||
        adoptedCount != summary.adoptedCount || isLatest != summary.isLatest

// ---- analytics ↔ DTO ---------------------------------------------------------------------------------------------

internal fun WeeklyStatsDto.toAnalytics(): WeeklyStats = WeeklyStats(
    analyzedTotals = Totals(analyzedTotals.frequency, analyzedTotals.quantity),
    categories = categories.map { CategoryStats(it.category.name, it.frequency, it.quantity, it.sharePct) },
    subcategories = subcategories.map { SubcategoryStats(it.code, it.category.name, it.frequency, it.quantity) },
    special = Totals(special.frequency, special.quantity),
)

internal fun WeeklyStats.toDto(): WeeklyStatsDto = WeeklyStatsDto(
    analyzedTotals = TotalsDto(analyzedTotals.frequency, analyzedTotals.quantity),
    categories = categories.map { CategoryStatsDto(WasteCategory.valueOf(it.category), it.frequency, it.quantity, it.sharePct) },
    subcategories = subcategories.map {
        SubcategoryStatsDto(it.code, WasteCategory.valueOf(it.category), it.frequency, it.quantity)
    },
    special = TotalsDto(special.frequency, special.quantity),
)

internal fun Hotspot.toDto(): HotspotDto = HotspotDto(
    rank = rank,
    subcategory = subcategory,
    criteria = criteria.map { it.toDto() },
    frequency = frequency,
    quantity = quantity,
    score = score,
    recommendations = emptyList(), // Never offline (plan §5.7).
)

internal fun Comparison.toDto(): ComparisonDto = ComparisonDto(
    previousWeekStart = LocalDate.parse(previousWeekStart),
    total = ComparisonRowDto(total.previous, total.current, total.delta, total.deltaPct, total.trend.toDto()),
    categories = categories.map {
        CategoryComparisonDto(WasteCategory.valueOf(it.category), it.previous, it.current, it.delta, it.deltaPct, it.trend.toDto())
    },
    subcategories = subcategories.map {
        SubcategoryComparisonDto(
            it.code, WasteCategory.valueOf(it.category), it.previous, it.current, it.delta, it.deltaPct, it.trend.toDto(),
        )
    },
)

internal fun AnalyticsTrend.toDto(): Trend = Trend.valueOf(name)

internal fun AnalyticsCriterion.toDto(): HotspotCriterion = HotspotCriterion.valueOf(name)
