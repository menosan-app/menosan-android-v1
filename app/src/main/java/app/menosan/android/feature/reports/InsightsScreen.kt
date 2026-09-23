package app.menosan.android.feature.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.menosan.android.R
import app.menosan.android.core.ui.components.MessageBanner
import app.menosan.android.core.ui.components.Pill
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.data.repo.RefreshResult
import app.menosan.android.data.repo.ReportListItem
import app.menosan.android.data.repo.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// Insights tab (HistoryRoute): the weekly reports, newest first (plan §10 "History", mockup `Insights Dashboard`).

data class InsightsUiState(
    val reports: List<ReportListItem> = emptyList(),
    /** The cache has been read at least once. */
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val problem: ReportProblem? = null,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: ReportRepository,
) : ViewModel() {
    private val status = MutableStateFlow(InsightsUiState(refreshing = true))

    val state: StateFlow<InsightsUiState> = combine(repository.observeReports(), status) { reports, s ->
        s.copy(reports = reports, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState(refreshing = true))

    init {
        refresh()
    }

    fun refresh() {
        status.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val result = repository.refreshReports()
            status.update {
                it.copy(refreshing = false, problem = (result as? RefreshResult.Failure)?.error?.toProblem())
            }
        }
    }
}

@Composable
fun InsightsScreen(onOpenReport: (LocalDate) -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsContent(state = state, onRefresh = viewModel::refresh, onOpenReport = onOpenReport)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsContent(state: InsightsUiState, onRefresh: () -> Unit, onOpenReport: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.insights_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                stringResource(R.string.insights_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing && state.reports.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val reports = state.reports
                when {
                    reports.isEmpty() && (state.refreshing || !state.loaded) -> item {
                        PatientLoading(stringResource(R.string.insights_loading))
                    }
                    reports.isEmpty() && state.problem != null -> item {
                        ProblemWithRetry(state.problem, onRetry = onRefresh)
                    }
                    reports.isEmpty() -> item {
                        GentleMessage(
                            title = stringResource(R.string.insights_empty_title),
                            body = stringResource(R.string.insights_empty_body),
                            hint = stringResource(R.string.insights_empty_hint),
                        )
                    }
                    else -> {
                        if (state.problem != null) {
                            item { MessageBanner(stringResource(R.string.insights_showing_saved), icon = Icons.Outlined.CloudOff) }
                        }
                        item { LatestReportCard(reports.first(), onClick = { onOpenReport(reports.first().weekStart) }) }
                        if (reports.size > 1) {
                            item {
                                Text(
                                    stringResource(R.string.insights_past_reports),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(reports.drop(1), key = { it.weekStart.toString() }) { report ->
                                ReportRow(report, onClick = { onOpenReport(report.weekStart) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun reportStats(report: ReportListItem): String {
    val base = stringResource(
        R.string.insights_report_stats,
        piecesText(report.analyzedQuantity),
        pluralStringResource(R.plurals.reports_hotspots, report.hotspotCount, report.hotspotCount),
    )
    return if (report.adoptedCount > 0) "$base · ${pluralStringResource(R.plurals.insights_adopted, report.adoptedCount, report.adoptedCount)}" else base
}

/** The newest report, highlighted as a Moss hero card. */
@Composable
private fun LatestReportCard(report: ReportListItem, onClick: () -> Unit) {
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.insights_latest_label), style = MaterialTheme.typography.titleSmall, color = onHero)
                Spacer(Modifier.weight(1f))
                if (report.isProvisional) {
                    Pill(stringResource(R.string.reports_offline_summary), MenosanTheme.colors.calm, MenosanTheme.colors.onCalm)
                }
            }
            Text(
                weekRangeWithYear(report.weekStart, report.weekEnd),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = onHero,
            )
            Text(reportStats(report), style = MaterialTheme.typography.bodyMedium, color = onHero)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.insights_open_report), style = MaterialTheme.typography.labelLarge, color = onHero)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = onHero)
            }
        }
    }
}

@Composable
private fun ReportRow(report: ReportListItem, onClick: () -> Unit) {
    ReportCard(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    weekRangeWithYear(report.weekStart, report.weekEnd),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    reportStats(report),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (report.isProvisional) {
                    Pill(stringResource(R.string.reports_offline_summary), MenosanTheme.colors.calm, MenosanTheme.colors.onCalm)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
