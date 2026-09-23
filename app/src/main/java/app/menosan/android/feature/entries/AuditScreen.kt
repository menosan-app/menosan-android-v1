package app.menosan.android.feature.entries

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.menosan.android.R
import app.menosan.android.core.model.Entry
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.ui.components.BannerTone
import app.menosan.android.core.ui.components.MessageBanner
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.feature.logging.icon
import app.menosan.android.feature.logging.labelRes
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Audit tab (mockups `Waste Audit Dashboard`, `Waste Audit History`, `… (Pending Entries)`). */
@Composable
fun AuditRoute(
    onLogManually: () -> Unit,
    onScanWithPhoto: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onEditEntry: (String) -> Unit,
    viewModel: AuditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messageEvents.collect { snackbar.showSnackbar(resources.getString(it)) }
    }
    Box(Modifier.fillMaxSize()) {
        AuditScreen(
            state = state,
            onLogManually = onLogManually,
            onScanWithPhoto = onScanWithPhoto,
            onOpenEntry = onOpenEntry,
            onEditEntry = onEditEntry,
            onDeleteEntry = viewModel::delete,
            onDismissReverted = viewModel::dismissRevertedNotice,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
fun AuditScreen(
    state: AuditUiState,
    onLogManually: () -> Unit,
    onScanWithPhoto: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onEditEntry: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onDismissReverted: () -> Unit,
) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var showEarlier by rememberSaveable { mutableStateOf(false) }
    val summary = state.summary

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    stringResource(R.string.audit_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.audit_subtitle), style = MaterialTheme.typography.bodyLarge)
            }
        }
        item { WeekCard(state, summary) }
        item { QuickActions(onLogManually, onScanWithPhoto) }

        if (state.revertedChanges > 0) {
            item {
                Column {
                    MessageBanner(
                        title = stringResource(R.string.audit_reverted_title),
                        text = pluralStringResource(R.plurals.audit_reverted_body, state.revertedChanges, state.revertedChanges),
                    )
                    TextButton(onClick = onDismissReverted, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.audit_dismiss))
                    }
                }
            }
        }
        if (state.pendingCount > 0) {
            item {
                MessageBanner(
                    title = stringResource(R.string.audit_pending_title),
                    text = pluralStringResource(R.plurals.audit_pending_body, state.pendingCount, state.pendingCount),
                    icon = Icons.Outlined.HourglassEmpty,
                )
            }
        }
        if (state.failedCount > 0) {
            item {
                MessageBanner(
                    title = stringResource(R.string.audit_failed_title),
                    text = stringResource(R.string.audit_failed_body),
                    tone = BannerTone.Error,
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.audit_entries_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    pluralStringResource(R.plurals.audit_entries, summary.entries, summary.entries),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.entries.isEmpty() && !state.loading) {
            item { EmptyWeek() }
        } else {
            entryCard(state, state.entries, onOpenEntry, onEditEntry) { pendingDelete = it }
        }

        if (state.earlierWeeks.isNotEmpty()) {
            item {
                TextButton(onClick = { showEarlier = !showEarlier }) {
                    Text(stringResource(if (showEarlier) R.string.audit_earlier_hide else R.string.audit_earlier_show))
                }
            }
            if (showEarlier) {
                item {
                    Text(
                        stringResource(R.string.audit_earlier_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.earlierWeeks.forEach { week ->
                    item(key = "week-${week.weekStart}") {
                        Text(
                            EntryFormats.weekRange(week.weekStart),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                        )
                    }
                    entryCard(state, week.entries, onOpenEntry, onEditEntry) { pendingDelete = it }
                }
            }
        }
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

/** A white outlined card holding entry rows (one list item per row, so long weeks stay lazy). */
private fun LazyListScope.entryCard(
    state: AuditUiState,
    entries: List<Entry>,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    items(entries, key = { it.id }) { entry ->
        Surface(
            color = MenosanTheme.colors.card,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            EntryRow(
                entry = entry,
                subcategoryLabel = state.labelOf(entry),
                onOpen = { onOpen(entry.id) },
                onEdit = { onEdit(entry.id) },
                onDelete = { onDelete(entry.id) },
            )
        }
    }
}

@Composable
private fun WeekCard(state: AuditUiState, summary: WeekSummary) {
    Surface(
        color = MenosanTheme.colors.card,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.audit_this_week), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    EntryFormats.weekRange(state.weekStart),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        summary.entries.toString(),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        stringResource(
                            R.string.audit_entries_and_pieces,
                            pluralStringResource(R.plurals.audit_entries_word, summary.entries),
                            pluralStringResource(R.plurals.audit_pieces, summary.pieces, summary.pieces),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.audit_logged_this_week),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DayBars(summary.entriesPerDay, EntryFormats.dayIndex(state.today).takeIf { state.today >= state.weekStart } ?: -1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WasteCategory.entries.forEach { category ->
                    CategoryTile(category, summary.byCategory[category] ?: CategoryTotal(), Modifier.weight(1f))
                }
            }
        }
    }
}

/** Sun–Sat entry counts (DESIGN.md §3 charts). Sage bars, today in Moss. Drawn with Canvas, no chart library. */
@Composable
private fun DayBars(perDay: List<Int>, todayIndex: Int) {
    val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val bar = MenosanTheme.colors.calm
    val today = MaterialTheme.colorScheme.primaryContainer
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val days = DayOfWeek.entries.let { listOf(DayOfWeek.SUNDAY) + it.dropLast(1) }
    val labels = days.map { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    val description = stringResource(R.string.audit_bars_description, labels.zip(perDay).joinToString { (d, n) -> "$d $n" })
    Column(Modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.size(width = 182.dp, height = 64.dp)) {
            val slot = size.width / 7f
            val width = slot * 0.6f
            val radius = CornerRadius(width / 3f, width / 3f)
            perDay.forEachIndexed { i, count ->
                val left = i * slot + (slot - width) / 2f
                drawRoundRect(track, Offset(left, 0f), Size(width, size.height), radius)
                if (count > 0) {
                    val h = (size.height * count / max).coerceAtLeast(width / 2f)
                    drawRoundRect(if (i == todayIndex) today else bar, Offset(left, size.height - h), Size(width, h), radius)
                }
            }
        }
        Row(Modifier.size(width = 182.dp, height = 16.dp)) {
            labels.forEach {
                Text(
                    it.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CategoryTile(category: WasteCategory, total: CategoryTotal, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MenosanTheme.colors.card,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(category.icon(), contentDescription = null, tint = category.color(), modifier = Modifier.size(26.dp))
            Text(
                stringResource(category.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                pluralStringResource(R.plurals.audit_entries, total.entries, total.entries),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickActions(onLogManually: () -> Unit, onScanWithPhoto: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.audit_quick_actions),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(Icons.Outlined.EditNote, R.string.audit_log_manual, R.string.audit_log_manual_body, onLogManually, Modifier.weight(1f))
            QuickAction(Icons.Outlined.CameraAlt, R.string.audit_scan, R.string.audit_scan_body, onScanWithPhoto, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, title: Int, body: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MenosanTheme.colors.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyWeek() {
    Surface(color = MenosanTheme.colors.mist, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.audit_empty_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.audit_empty_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
