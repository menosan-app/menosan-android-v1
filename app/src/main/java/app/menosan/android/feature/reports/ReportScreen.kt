package app.menosan.android.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.menosan.android.R
import app.menosan.android.core.ui.components.MessageBanner
import app.menosan.android.core.ui.components.Pill
import app.menosan.android.core.ui.components.ScreenHeader
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.data.remote.dto.ComparisonDto
import app.menosan.android.data.remote.dto.HotspotDto
import app.menosan.android.data.remote.dto.RecommendationDto
import app.menosan.android.data.remote.dto.Trend
import app.menosan.android.data.remote.dto.WeeklyStatsDto
import app.menosan.android.data.repo.LastWeekRecap
import app.menosan.android.data.repo.ReportView
import app.menosan.android.feature.interventions.ImpactCard
import app.menosan.android.feature.interventions.RecommendationCard
import app.menosan.android.feature.interventions.RecommendationDetailsSheet

// Weekly report (ReportRoute): Weekly Summary, Waste Hotspot, and Intervention Results of plan §10, in one scrolling
// screen. Mockups `Waste Hotspot` and `Prevention Recommendation (New/Adopted)` are the style reference.

@Composable
fun ReportScreen(onBack: () -> Unit, viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                ReportMessage.Adopted -> R.string.report_adopt_done
                ReportMessage.Unadopted -> R.string.report_unadopt_done
                ReportMessage.WindowClosed -> R.string.report_adopt_window_closed
                ReportMessage.Offline -> R.string.report_adopt_offline
                ReportMessage.Failed -> R.string.report_adopt_failed
            }
            snackbar.showSnackbar(resources.getString(text))
        }
    }
    Box(Modifier.fillMaxSize()) {
        ReportContent(state = state, onBack = onBack, onRefresh = viewModel::refresh, onSetAdopted = viewModel::setAdopted)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportContent(
    state: ReportUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetAdopted: (interventionId: String, adopted: Boolean) -> Unit,
) {
    var details by remember { mutableStateOf<String?>(null) } // Intervention id of the open details sheet.
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.nav_report), onBack = onBack)
        val view = state.view
        PullToRefreshBox(
            isRefreshing = state.refreshing && view != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    view != null -> reportSections(state, view, onSetAdopted, onOpenDetails = { details = it })
                    state.notFound -> item { GentleMessage(title = null, body = stringResource(R.string.report_not_found)) }
                    state.refreshing || !state.loaded -> item { PatientLoading(stringResource(R.string.report_loading)) }
                    state.problem != null -> item { ProblemWithRetry(state.problem, onRetry = onRefresh) }
                    else -> item { GentleMessage(title = null, body = stringResource(R.string.report_not_found)) }
                }
            }
        }
    }
    val view = state.view
    val open = details?.let { id -> view?.report?.hotspots?.flatMap { it.recommendations }?.firstOrNull { it.interventionId == id } }
    if (view != null && open != null) {
        RecommendationDetailsSheet(
            rec = open,
            canAdopt = view.canAdopt,
            pending = open.interventionId in state.pendingAdoptions,
            onToggleAdopt = { onSetAdopted(open.interventionId, !open.adopted) },
            onDismiss = { details = null },
        )
    }
}

private fun LazyListScope.reportSections(
    state: ReportUiState,
    view: ReportView,
    onSetAdopted: (String, Boolean) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val report = view.report
    item(key = "header") { WeekHeader(view) }
    if (view.isProvisional) {
        item(key = "offline") {
            val text = stringResource(R.string.report_offline_banner) +
                if (view.missingComparisons) "\n" + stringResource(R.string.report_offline_banner_partial) else ""
            MessageBanner(text, icon = Icons.Outlined.CloudOff)
        }
    } else if (state.problem != null) {
        item(key = "saved") { MessageBanner(stringResource(R.string.report_showing_saved), icon = Icons.Outlined.CloudOff) }
    }
    item(key = "totals") { TotalsCard(report.stats) }
    item(key = "categories") { CategoryBarsCard(report.stats, state) }
    item(key = "breakdown") { BreakdownCard(report.stats, state) }
    item(key = "comparison") { ComparisonCard(report.comparison, state) }
    item(key = "hotspots") {
        SectionTitle(stringResource(R.string.report_hotspots_title), subtitle = stringResource(R.string.report_hotspots_subtitle))
    }
    if (report.hotspots.isEmpty()) {
        item(key = "special-only") { MessageBanner(stringResource(R.string.report_hotspots_special_only)) }
    } else {
        items(report.hotspots, key = { "hotspot-${it.subcategory}" }) { HotspotCard(it, state) }
    }
    // Recommendations and adoption are online-only (plan §5.7): never on a provisional report.
    if (!view.isProvisional && report.hotspots.isNotEmpty()) {
        item(key = "ideas") {
            IdeasSection(view, state, onSetAdopted, onOpenDetails)
        }
    }
    if (report.impacts.isNotEmpty()) {
        item(key = "impact-title") {
            SectionTitle(stringResource(R.string.report_impact_title), subtitle = stringResource(R.string.report_impact_subtitle))
        }
        items(report.impacts, key = { "impact-${it.interventionId}" }) { ImpactCard(it, state.label(it.targetSubcategory)) }
    }
    view.recap?.let { recap -> item(key = "recap") { RecapCard(recap, state) } }
}

@Composable
private fun WeekHeader(view: ReportView) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            weekRangeWithYear(view.weekStart, view.weekEnd),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (view.isProvisional) {
                Pill(stringResource(R.string.reports_offline_summary), MenosanTheme.colors.calm, MenosanTheme.colors.onCalm)
            }
            if (view.canAdopt) {
                Pill(stringResource(R.string.reports_latest), MenosanTheme.colors.highlight, MenosanTheme.colors.onHighlight)
            }
        }
    }
}

@Composable
private fun TotalsCard(stats: WeeklyStatsDto) {
    ReportCard {
        SectionTitle(stringResource(R.string.report_totals_title))
        Row(Modifier.fillMaxWidth()) {
            BigNumber(stats.analyzedTotals.frequency, stringResource(R.string.report_totals_entries), Modifier.weight(1f))
            BigNumber(stats.analyzedTotals.quantity, stringResource(R.string.report_totals_pieces), Modifier.weight(1f))
        }
    }
}

@Composable
private fun BigNumber(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value.toString(), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Main-category horizontal bars (plan §10 "main-category bar chart"), drawn with Canvas. */
@Composable
private fun CategoryBarsCard(stats: WeeklyStatsDto, state: ReportUiState) {
    ReportCard {
        SectionTitle(stringResource(R.string.report_categories_title))
        stats.categories.forEach { category ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.label(category.category.name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.report_category_value, piecesText(category.quantity), formatShare(category.sharePct)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ShareBar((category.sharePct / 100.0).toFloat(), categoryColor(category.category))
            }
        }
    }
}

private fun formatShare(value: Double): String = String.format(java.util.Locale.ENGLISH, "%.1f", value)

@Composable
private fun CategoryDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

/** Subcategory breakdown with taxonomy labels, and the Special line (I3). */
@Composable
private fun BreakdownCard(stats: WeeklyStatsDto, state: ReportUiState) {
    ReportCard {
        SectionTitle(stringResource(R.string.report_breakdown_title))
        if (stats.subcategories.isEmpty()) {
            Text(stringResource(R.string.report_breakdown_empty), style = MaterialTheme.typography.bodyMedium)
        }
        stats.subcategories.forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryDot(categoryColor(sub.category))
                Text(state.label(sub.code), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    entriesAndPieces(sub.frequency, sub.quantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (stats.special.frequency > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryDot(MenosanTheme.colors.special)
                Text(
                    stringResource(R.string.report_special_line, entriesAndPieces(stats.special.frequency, stats.special.quantity)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun trendIcon(trend: Trend): Pair<ImageVector, Color> = when (trend) {
    Trend.DECREASED -> Icons.AutoMirrored.Filled.TrendingDown to MaterialTheme.colorScheme.primary
    Trend.INCREASED -> Icons.AutoMirrored.Filled.TrendingUp to MaterialTheme.colorScheme.tertiary
    Trend.SAME, Trend.UNKNOWN -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
}

/** Week-over-week comparison in pieces (plan §5.3), or the "no comparison" message. */
@Composable
private fun ComparisonCard(comparison: ComparisonDto?, state: ReportUiState) {
    ReportCard {
        SectionTitle(stringResource(R.string.report_comparison_title))
        if (comparison == null) {
            Text(stringResource(R.string.report_comparison_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            ComparisonDetails(comparison, state)
        }
    }
}

@Composable
private fun ComparisonDetails(comparison: ComparisonDto, state: ReportUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val total = comparison.total
        val (icon, tint) = trendIcon(total.trend)
        Text(
            stringResource(R.string.report_comparison_week, formatWeekRange(comparison.previousWeekStart, comparison.previousWeekStart.plusDays(6))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = tint)
            Text(
                stringResource(R.string.report_comparison_total, total.previous, total.current),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            total.deltaPct?.let {
                Text(
                    stringResource(R.string.report_comparison_pct, signedPercent(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            when (total.trend) {
                Trend.DECREASED -> pluralStringResource(R.plurals.report_comparison_fewer, absInt(total.delta), absInt(total.delta))
                Trend.INCREASED -> pluralStringResource(R.plurals.report_comparison_more, absInt(total.delta), absInt(total.delta))
                else -> stringResource(R.string.report_comparison_same)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        comparison.categories.forEach { row ->
            ComparisonRow(state.label(row.category.name), row.previous, row.current, row.trend, categoryColor(row.category))
        }
        var showAll by rememberSaveable { mutableStateOf(false) }
        if (comparison.subcategories.isNotEmpty()) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(stringResource(if (showAll) R.string.report_comparison_hide_all else R.string.report_comparison_show_all))
            }
        }
        if (showAll) {
            comparison.subcategories.forEach { row ->
                ComparisonRow(state.label(row.code), row.previous, row.current, row.trend, categoryColor(row.category))
            }
        }
    }
}

@Composable
private fun ComparisonRow(label: String, previous: Int, current: Int, trend: Trend, color: Color) {
    val (icon, tint) = trendIcon(trend)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryDot(color)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.report_comparison_row, previous, current), style = MaterialTheme.typography.bodyMedium)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** A ranked hotspot with its criteria chips and entries/pieces (plan §5.2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotspotCard(hotspot: HotspotDto, state: ReportUiState) {
    ReportCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(hotspot.rank.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(state.label(hotspot.subcategory), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    entriesAndPieces(hotspot.frequency, hotspot.quantity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (hotspot.rank == 1) {
                Pill(stringResource(R.string.report_top_hotspot), MenosanTheme.colors.highlight, MenosanTheme.colors.onHighlight)
            }
            hotspot.criteria.mapNotNull { criterionLabel(it) }.forEach {
                Pill(it, MenosanTheme.colors.pending, MenosanTheme.colors.onPending)
            }
        }
    }
}

/** Recommendations per hotspot, with a hotspot picker (mockup "Choose Hotspot"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdeasSection(
    view: ReportView,
    state: ReportUiState,
    onSetAdopted: (String, Boolean) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val hotspots = view.report.hotspots
    var selected by rememberSaveable(view.weekStart.toString()) { mutableIntStateOf(0) }
    val hotspot = hotspots[selected.coerceIn(0, hotspots.lastIndex)]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            stringResource(R.string.report_ideas_title),
            subtitle = stringResource(if (view.canAdopt) R.string.report_ideas_subtitle_latest else R.string.report_ideas_subtitle_closed),
        )
        if (hotspots.size > 1) {
            Text(stringResource(R.string.report_choose_hotspot), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hotspots.forEachIndexed { index, h ->
                    FilterChip(
                        selected = h == hotspot,
                        onClick = { selected = index },
                        label = { Text(state.label(h.subcategory)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }
        if (hotspot.recommendations.isEmpty()) {
            Text(stringResource(R.string.report_ideas_none), style = MaterialTheme.typography.bodyMedium)
        }
        hotspot.recommendations.forEach { rec: RecommendationDto ->
            RecommendationCard(
                rec = rec,
                canAdopt = view.canAdopt,
                pending = rec.interventionId in state.pendingAdoptions,
                notMeasured = view.followupNotMeasured,
                onToggleAdopt = { onSetAdopted(rec.interventionId, !rec.adopted) },
                onOpenDetails = { onOpenDetails(rec.interventionId) },
            )
        }
    }
}

/** Offline summary only: last week's hotspots and the ideas the user tried (plan §5.7). */
@Composable
private fun RecapCard(recap: LastWeekRecap, state: ReportUiState) {
    ReportCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            SectionTitle(stringResource(R.string.report_recap_title, formatWeekRange(recap.weekStart, recap.weekEnd)))
        }
        if (recap.hotspots.isNotEmpty()) {
            Text(
                stringResource(R.string.report_recap_hotspots, recap.hotspots.joinToString(", ") { state.label(it.subcategory) }),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (recap.adopted.isNotEmpty()) {
            Text(
                stringResource(R.string.report_recap_tried, recap.adopted.joinToString(", ") { it.title }),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
