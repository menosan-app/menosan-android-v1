package app.menosan.android.feature.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.menosan.android.R
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.network.ApiError
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.data.remote.dto.CostLevel
import app.menosan.android.data.remote.dto.Effort
import app.menosan.android.data.remote.dto.HotspotCriterion
import app.menosan.android.data.remote.dto.InterventionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// Formatting shared by the Insights and weekly report screens (AN-3).

private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)

/** "Sep 20 – 26" within a month, "Sep 27 – Oct 3" across months. */
fun formatWeekRange(start: LocalDate, end: LocalDate): String =
    if (start.month == end.month) "${MONTH_DAY.format(start)} – ${DAY.format(end)}" else "${MONTH_DAY.format(start)} – ${MONTH_DAY.format(end)}"

@Composable
@ReadOnlyComposable
fun weekRangeWithYear(start: LocalDate, end: LocalDate): String =
    stringResource(R.string.reports_week_range_year, formatWeekRange(start, end), end.year)

@Composable
@ReadOnlyComposable
fun entriesText(count: Int): String = pluralStringResource(R.plurals.reports_entries, count, count)

@Composable
@ReadOnlyComposable
fun piecesText(count: Int): String = pluralStringResource(R.plurals.reports_pieces, count, count)

@Composable
@ReadOnlyComposable
fun entriesAndPieces(entries: Int, pieces: Int): String =
    stringResource(R.string.reports_entries_and_pieces, entriesText(entries), piecesText(pieces))

/** One decimal, with an explicit sign: "-9.2", "+12.5". */
fun signedPercent(value: Double): String = String.format(Locale.ENGLISH, "%+.1f", value)

fun absInt(value: Int): Int = abs(value)

@Composable
@ReadOnlyComposable
fun categoryColor(category: WasteCategory): Color = when (category) {
    WasteCategory.BIODEGRADABLE -> MenosanTheme.colors.biodegradable
    WasteCategory.RECYCLABLE -> MenosanTheme.colors.recyclable
    WasteCategory.RESIDUAL -> MenosanTheme.colors.residual
    WasteCategory.SPECIAL -> MenosanTheme.colors.special
}

@Composable
@ReadOnlyComposable
fun criterionLabel(criterion: HotspotCriterion): String? = when (criterion) {
    HotspotCriterion.MOST_FREQUENT -> stringResource(R.string.report_criterion_most_frequent)
    HotspotCriterion.HIGHEST_QUANTITY -> stringResource(R.string.report_criterion_highest_quantity)
    HotspotCriterion.AVOIDABLE -> stringResource(R.string.report_criterion_avoidable)
    HotspotCriterion.UNKNOWN -> null
}

@Composable
@ReadOnlyComposable
fun typeLabel(type: InterventionType): String? = when (type) {
    InterventionType.PREVENT -> stringResource(R.string.report_type_prevent)
    InterventionType.REDUCE -> stringResource(R.string.report_type_reduce)
    InterventionType.REUSE -> stringResource(R.string.report_type_reuse)
    InterventionType.UNKNOWN -> null
}

@Composable
@ReadOnlyComposable
fun costLabel(cost: CostLevel): String? = when (cost) {
    CostLevel.FREE -> stringResource(R.string.report_cost_free)
    CostLevel.SAVES_MONEY -> stringResource(R.string.report_cost_saves_money)
    CostLevel.SMALL_ONE_TIME_COST -> stringResource(R.string.report_cost_small_one_time)
    CostLevel.UNKNOWN -> null
}

@Composable
@ReadOnlyComposable
fun effortLabel(effort: Effort): String? = when (effort) {
    Effort.LOW -> stringResource(R.string.report_effort_low)
    Effort.MEDIUM -> stringResource(R.string.report_effort_medium)
    Effort.UNKNOWN -> null
}

/** What went wrong when refreshing, reduced to what the user can act on. */
enum class ReportProblem { Offline, Server }

fun ApiError.toProblem(): ReportProblem = if (this is ApiError.Network) ReportProblem.Offline else ReportProblem.Server

@Composable
@ReadOnlyComposable
fun problemText(problem: ReportProblem): String = when (problem) {
    ReportProblem.Offline -> stringResource(R.string.reports_error_offline)
    ReportProblem.Server -> stringResource(R.string.reports_error_server)
}
