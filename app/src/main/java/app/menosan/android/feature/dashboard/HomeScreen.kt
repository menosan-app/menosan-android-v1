package app.menosan.android.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiObjects
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.menosan.android.R
import app.menosan.android.core.ui.components.BannerTone
import app.menosan.android.core.ui.components.MenosanWordmark
import app.menosan.android.core.ui.components.MessageBanner
import app.menosan.android.core.ui.components.Pill
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.data.remote.dto.ImpactDto
import app.menosan.android.data.remote.dto.Trend
import app.menosan.android.feature.entries.DeleteEntryDialog
import app.menosan.android.feature.entries.EntryFormats
import app.menosan.android.feature.entries.EntryRow
import app.menosan.android.feature.reports.formatWeekRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Where Home can send the user. Routes live in the navigation file. */
data class HomeActions(
    val onLogManually: () -> Unit,
    val onLogWithPhoto: () -> Unit,
    val onOpenReport: (LocalDate) -> Unit,
    val onViewAllEntries: () -> Unit,
    val onOpenEntry: (String) -> Unit,
    val onEditEntry: (String) -> Unit,
)

/** Home tab (mockup `Home`, style only; behavior from plan §10 Dashboard and DESIGN.md §5.4). */
@Composable
fun HomeRoute(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messageEvents.collect { snackbar.showSnackbar(resources.getString(it)) }
    }
    Box(Modifier.fillMaxSize()) {
        HomeScreen(state = state, actions = actions, onDeleteEntry = viewModel::delete)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, onDeleteEntry: (String) -> Unit) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MenosanWordmark(Modifier.weight(1f))
            ConnectionPill(online = state.online)
        }
        Column {
            Text(
                text = state.firstName?.let { stringResource(R.string.dashboard_greeting_name, it) }
                    ?: stringResource(R.string.dashboard_greeting),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        WeekHero(state)
        QuickActions(actions)

        if (state.pendingCount > 0) {
            MessageBanner(
                title = stringResource(R.string.dashboard_pending_title),
                text = pluralStringResource(R.plurals.dashboard_pending_body, state.pendingCount, state.pendingCount),
                icon = Icons.Outlined.HourglassEmpty,
            )
        }
        if (state.failedCount > 0) {
            MessageBanner(
                title = stringResource(R.string.dashboard_failed_title),
                text = stringResource(R.string.dashboard_failed_body),
                tone = BannerTone.Error,
                modifier = Modifier.clickable(role = Role.Button, onClick = actions.onViewAllEntries),
            )
        }

        val report = state.latestReport
        if (report == null) {
            FirstReportCard()
        } else {
            ReportCards(report, state, actions)
        }

        RecentEntries(state, actions) { pendingDelete = it }
    }

    pendingDelete?.let { id ->
        DeleteEntryDialog(
            onConfirm = {
                pendingDelete = null
                onDeleteEntry(id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** The "Online" / "Offline" pill of the Home header. */
@Composable
private fun ConnectionPill(online: Boolean) {
    val color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = stringResource(if (online) R.string.status_online else R.string.status_offline),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .border(BorderStroke(1.5.dp, color), MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Moss hero card: this week's range, live entry and piece counts, and Sun–Sat bars. No live comparisons. */
@Composable
private fun WeekHero(state: HomeUiState) {
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    val summary = state.summary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dashboard_this_week),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = onHero,
                modifier = Modifier.weight(1f),
            )
            Text(EntryFormats.weekRange(state.weekStart), style = MaterialTheme.typography.labelLarge, color = onHero)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                summary.entries.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = onHero,
            )
            Text(
                stringResource(
                    R.string.dashboard_entries_and_pieces,
                    pluralStringResource(R.plurals.dashboard_entries_word, summary.entries),
                    pluralStringResource(R.plurals.dashboard_pieces, summary.pieces, summary.pieces),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = onHero,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (summary.entries == 0) {
            Text(stringResource(R.string.dashboard_week_empty), style = MaterialTheme.typography.bodyMedium, color = onHero)
        }
        val todayIndex = if (state.today >= state.weekStart) EntryFormats.dayIndex(state.today) else -1
        DayBars(summary.entriesPerDay, todayIndex, onHero)
    }
}

/** Sun–Sat entries per day, drawn with Canvas (DESIGN.md §3). Today is fully opaque. Stretches to the card width. */
@Composable
private fun DayBars(perDay: List<Int>, todayIndex: Int, color: Color) {
    val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val days = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.dropLast(1)
    val labels = days.map { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    val description = stringResource(R.string.dashboard_bars_description, labels.zip(perDay).joinToString { (d, n) -> "$d $n" })
    val bar = color.copy(alpha = 0.45f)
    val track = color.copy(alpha = 0.12f)
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val slot = size.width / 7f
            val width = (slot * 0.5f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(width / 3f, width / 3f)
            perDay.forEachIndexed { i, count ->
                val left = i * slot + (slot - width) / 2f
                drawRoundRect(track, Offset(left, 0f), Size(width, size.height), radius)
                if (count > 0) {
                    val h = (size.height * count / max).coerceAtLeast(width / 2f)
                    drawRoundRect(if (i == todayIndex) color else bar, Offset(left, size.height - h), Size(width, h), radius)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (i == todayIndex) FontWeight.Bold else FontWeight.Normal),
                    color = color,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuickActions(actions: HomeActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickActionButton(Icons.Outlined.EditNote, stringResource(R.string.dashboard_log_manual), actions.onLogManually, Modifier.weight(1f))
        QuickActionButton(Icons.Outlined.CameraAlt, stringResource(R.string.dashboard_log_photo), actions.onLogWithPhoto, Modifier.weight(1f))
    }
}

@Composable
private fun QuickActionButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.heightIn(min = 52.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** White outlined card; with [onClick] it gets a trailing chevron and opens a detail screen (DESIGN.md §3). */
@Composable
private fun HomeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MenosanTheme.colors.card,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = (if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
            if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CardTitle(text: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FirstReportCard() {
    Surface(color = MenosanTheme.colors.mist, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.dashboard_first_report_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.dashboard_first_report_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Latest report, its top hotspot, the ideas being tried, and last week's results. All from a closed week. */
@Composable
private fun ReportCards(report: LatestReportCard, state: HomeUiState, actions: HomeActions) {
    val range = formatWeekRange(report.weekStart, report.weekEnd)
    val open = { actions.onOpenReport(report.weekStart) }
    val openLabel = stringResource(R.string.dashboard_report_open)

    HomeCard(onClick = open, onClickLabel = openLabel) {
        CardTitle(
            text = when {
                report.isProvisional -> stringResource(R.string.dashboard_report_offline, range)
                report.isLastWeek -> stringResource(R.string.dashboard_report_ready, range)
                else -> stringResource(R.string.dashboard_report_latest, range)
            },
            icon = if (report.isProvisional) Icons.Outlined.CloudOff else Icons.Outlined.Insights,
        )
        Muted(
            stringResource(
                R.string.dashboard_entries_and_pieces,
                pluralStringResource(R.plurals.dashboard_entries, report.analyzedEntries, report.analyzedEntries),
                pluralStringResource(R.plurals.dashboard_pieces, report.analyzedPieces, report.analyzedPieces),
            ),
        )
        if (report.isProvisional) {
            Pill(stringResource(R.string.dashboard_offline_pill), MenosanTheme.colors.calm, MenosanTheme.colors.onCalm, icon = Icons.Outlined.CloudOff)
            Text(stringResource(R.string.dashboard_report_offline_body), style = MaterialTheme.typography.bodyMedium)
        }
    }

    val hotspot = report.topHotspot
    if (hotspot != null) {
        HomeCard(onClick = open, onClickLabel = openLabel) {
            CardTitle(stringResource(R.string.dashboard_hotspot_title), Icons.Outlined.LocalFireDepartment)
            Muted(stringResource(R.string.dashboard_from_report, range))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.labelOf(hotspot.subcategory),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Pill(stringResource(R.string.dashboard_top_hotspot_pill), MenosanTheme.colors.highlight, MenosanTheme.colors.onHighlight)
            }
            Text(
                stringResource(
                    R.string.dashboard_entries_and_pieces,
                    pluralStringResource(R.plurals.dashboard_entries, hotspot.frequency, hotspot.frequency),
                    pluralStringResource(R.plurals.dashboard_pieces, hotspot.quantity, hotspot.quantity),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else if (report.analyzedEntries == 0) {
        Muted(stringResource(R.string.dashboard_report_only_special))
    }

    if (report.trying.isNotEmpty()) {
        HomeCard(onClick = open, onClickLabel = openLabel) {
            CardTitle(stringResource(R.string.dashboard_trying_title), Icons.Outlined.EmojiObjects)
            report.trying.forEach { title ->
                Text("• $title", style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else if (report.hasIdeasToPick) {
        HomeCard(onClick = open, onClickLabel = openLabel) {
            CardTitle(stringResource(R.string.dashboard_pick_idea_title), Icons.Outlined.EmojiObjects)
            Text(stringResource(R.string.dashboard_pick_idea_body), style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (report.impacts.isNotEmpty()) {
        HomeCard(onClick = open, onClickLabel = openLabel) {
            CardTitle(stringResource(R.string.dashboard_impact_title), Icons.AutoMirrored.Outlined.TrendingDown)
            Muted(stringResource(R.string.dashboard_from_report, range))
            report.impacts.forEach { ImpactLine(it, state) }
        }
    }
}

@Composable
private fun ImpactLine(impact: ImpactDto, state: HomeUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(impact.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Muted(
                "${state.labelOf(impact.targetSubcategory)} · " +
                    stringResource(R.string.dashboard_impact_quantities, impact.baselineQuantity, impact.followupQuantity),
            )
        }
        val label = when (impact.result) {
            Trend.DECREASED -> R.string.dashboard_impact_decreased
            Trend.SAME -> R.string.dashboard_impact_same
            Trend.INCREASED -> R.string.dashboard_impact_increased
            Trend.UNKNOWN -> null
        }
        if (label != null) {
            val (bg, fg) = if (impact.result == Trend.DECREASED) {
                MenosanTheme.colors.calm to MenosanTheme.colors.onCalm
            } else {
                MenosanTheme.colors.pending to MenosanTheme.colors.onPending
            }
            Pill(stringResource(label), bg, fg)
        }
    }
}

@Composable
private fun RecentEntries(state: HomeUiState, actions: HomeActions, onDelete: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dashboard_recent_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.dashboard_recent_this_week),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            TextButton(onClick = actions.onViewAllEntries) {
                Text(stringResource(R.string.dashboard_view_all))
            }
        }
        if (state.recentEntries.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Surface(
                color = MenosanTheme.colors.card,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column {
                    state.recentEntries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        EntryRow(
                            entry = entry,
                            subcategoryLabel = state.labelOf(entry.subcategory),
                            onOpen = { actions.onOpenEntry(entry.id) },
                            onEdit = { actions.onEditEntry(entry.id) },
                            onDelete = { onDelete(entry.id) },
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

private val previewActions = HomeActions({}, {}, {}, {}, {}, {})

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun HomeNewUserPreview() {
    MenosanTheme {
        HomeScreen(
            state = HomeUiState.build(
                firstName = "Liza",
                weekStart = LocalDate.of(2026, 9, 27),
                today = LocalDate.of(2026, 9, 29),
                online = true,
                weekEntries = emptyList(),
                pendingCount = 0,
                latest = null,
                labels = emptyMap(),
            ),
            actions = previewActions,
            onDeleteEntry = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1200, backgroundColor = 0xFF171C19)
@Composable
private fun HomeNewUserDarkPreview() {
    MenosanTheme(darkTheme = true) {
        HomeScreen(
            state = HomeUiState.build(
                firstName = null,
                weekStart = LocalDate.of(2026, 9, 27),
                today = LocalDate.of(2026, 9, 29),
                online = false,
                weekEntries = emptyList(),
                pendingCount = 2,
                latest = null,
                labels = emptyMap(),
            ),
            actions = previewActions,
            onDeleteEntry = {},
        )
    }
}
