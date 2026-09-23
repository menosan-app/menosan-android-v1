package app.menosan.android.data.remote.dto

import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.network.FallbackEnumSerializer
import app.menosan.android.core.network.IsoDate
import kotlinx.serialization.Serializable

/** One row of `GET /v1/reports` (a bare array, newest first; contract §5.7). */
@Serializable
data class ReportSummaryDto(
    val weekStart: IsoDate,
    val weekEnd: IsoDate,
    val analyzedQuantity: Int,
    val hotspotCount: Int,
    val adoptedCount: Int,
    val isLatest: Boolean,
)

/** `GET /v1/reports/{weekStart}`, and the response of both adoption calls (contract §3, §5.7, §5.9). */
@Serializable
data class ReportDto(
    val weekStart: IsoDate,
    val weekEnd: IsoDate,
    val revision: Int,
    val isLatest: Boolean,
    val stats: WeeklyStatsDto,
    val hotspots: List<HotspotDto>,
    val comparison: ComparisonDto? = null,
    val impacts: List<ImpactDto> = emptyList(),
)

@Serializable
data class TotalsDto(val frequency: Int, val quantity: Int)

@Serializable
data class CategoryStatsDto(val category: WasteCategory, val frequency: Int, val quantity: Int, val sharePct: Double)

@Serializable
data class SubcategoryStatsDto(val code: String, val category: WasteCategory, val frequency: Int, val quantity: Int)

/** Plan §5.1. `categories` always lists the three analyzed categories. SPECIAL appears only in [special]. */
@Serializable
data class WeeklyStatsDto(
    val analyzedTotals: TotalsDto,
    val categories: List<CategoryStatsDto>,
    val subcategories: List<SubcategoryStatsDto>,
    val special: TotalsDto,
)

@Serializable(with = HotspotCriterionSerializer::class)
enum class HotspotCriterion { MOST_FREQUENT, HIGHEST_QUANTITY, AVOIDABLE, UNKNOWN }

object HotspotCriterionSerializer :
    FallbackEnumSerializer<HotspotCriterion>("HotspotCriterion", HotspotCriterion.entries.toTypedArray(), HotspotCriterion.UNKNOWN)

/** Plan §5.2. At most 3 per report, ordered by [rank]. */
@Serializable
data class HotspotDto(
    val rank: Int,
    val subcategory: String,
    val criteria: List<HotspotCriterion>,
    val frequency: Int,
    val quantity: Int,
    val score: Double,
    val recommendations: List<RecommendationDto> = emptyList(),
)

@Serializable(with = InterventionTypeSerializer::class)
enum class InterventionType { PREVENT, REDUCE, REUSE, UNKNOWN }

object InterventionTypeSerializer :
    FallbackEnumSerializer<InterventionType>("InterventionType", InterventionType.entries.toTypedArray(), InterventionType.UNKNOWN)

@Serializable(with = CostLevelSerializer::class)
enum class CostLevel { FREE, SAVES_MONEY, SMALL_ONE_TIME_COST, UNKNOWN }

object CostLevelSerializer : FallbackEnumSerializer<CostLevel>("CostLevel", CostLevel.entries.toTypedArray(), CostLevel.UNKNOWN)

@Serializable(with = EffortSerializer::class)
enum class Effort { LOW, MEDIUM, UNKNOWN }

object EffortSerializer : FallbackEnumSerializer<Effort>("Effort", Effort.entries.toTypedArray(), Effort.UNKNOWN)

/** A curated intervention picked for a hotspot (plan §6.2). [note] is Gemini's optional personal note. */
@Serializable
data class RecommendationDto(
    val interventionId: String,
    val code: String,
    val type: InterventionType,
    val title: String,
    val description: String,
    val howTo: List<String> = emptyList(),
    val costLevel: CostLevel,
    val effort: Effort,
    val note: String? = null,
    val continued: Boolean = false,
    val adopted: Boolean = false,
)

@Serializable(with = TrendSerializer::class)
enum class Trend { DECREASED, SAME, INCREASED, UNKNOWN }

object TrendSerializer : FallbackEnumSerializer<Trend>("Trend", Trend.entries.toTypedArray(), Trend.UNKNOWN)

/** Quantities in pieces. [deltaPct] is null when [previous] is 0. */
@Serializable
data class ComparisonRowDto(val previous: Int, val current: Int, val delta: Int, val deltaPct: Double? = null, val trend: Trend)

@Serializable
data class CategoryComparisonDto(
    val category: WasteCategory,
    val previous: Int,
    val current: Int,
    val delta: Int,
    val deltaPct: Double? = null,
    val trend: Trend,
)

@Serializable
data class SubcategoryComparisonDto(
    val code: String,
    val category: WasteCategory,
    val previous: Int,
    val current: Int,
    val delta: Int,
    val deltaPct: Double? = null,
    val trend: Trend,
)

/** Plan §5.3. Null on the report when the previous week has no analyzed entries. */
@Serializable
data class ComparisonDto(
    val previousWeekStart: IsoDate,
    val total: ComparisonRowDto,
    val categories: List<CategoryComparisonDto>,
    val subcategories: List<SubcategoryComparisonDto>,
)

/** Plan §5.4: how an intervention adopted on the previous report went this week. */
@Serializable
data class ImpactDto(
    val interventionId: String,
    val title: String,
    val targetSubcategory: String,
    val baselineWeekStart: IsoDate,
    val baselineQuantity: Int,
    val followupQuantity: Int,
    val result: Trend,
)

/** `POST /v1/reports/{weekStart}/adoptions` body: 1–9 intervention ids (contract §5.9). */
@Serializable
data class AdoptRequest(val interventionIds: List<String>)
