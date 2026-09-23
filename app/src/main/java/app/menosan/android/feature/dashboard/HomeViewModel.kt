package app.menosan.android.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.model.Entry
import app.menosan.android.core.model.EntrySyncStatus
import app.menosan.android.core.time.WeekCalc
import app.menosan.android.data.remote.dto.HotspotDto
import app.menosan.android.data.remote.dto.ImpactDto
import app.menosan.android.data.repo.EntryRepository
import app.menosan.android.data.repo.ReportRepository
import app.menosan.android.data.repo.ReportView
import app.menosan.android.data.repo.TaxonomyRepository
import app.menosan.android.data.repo.currentWeekStartFlow
import app.menosan.android.feature.entries.WeekSummary
import app.menosan.android.feature.entries.deleteMessage
import app.menosan.android.feature.settings.NetworkStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The latest report as Home shows it (plan §10 Dashboard). Everything here is about a **closed** week (DESIGN.md §5.4). */
data class LatestReportCard(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    /** Offline summary built on this phone (plan §5.7). */
    val isProvisional: Boolean,
    /** The report of the week that just ended (so it's "ready"), not an older one. */
    val isLastWeek: Boolean,
    val analyzedEntries: Int,
    val analyzedPieces: Int,
    /** Rank 1 hotspot, if the week had analyzed entries. */
    val topHotspot: HotspotDto?,
    /** Titles of the ideas adopted on this report, only while it still accepts adoptions (I7). */
    val trying: List<String>,
    /** The report is the latest one, has ideas, and none was picked yet: a gentle nudge. */
    val hasIdeasToPick: Boolean,
    /** How last week's picks went (plan §5.4), shown in this report. */
    val impacts: List<ImpactDto>,
) {
    companion object {
        fun from(view: ReportView, currentWeekStart: LocalDate): LatestReportCard {
            val report = view.report
            val trying = if (view.canAdopt) view.adopted.distinctBy { it.interventionId }.map { it.title } else emptyList()
            return LatestReportCard(
                weekStart = view.weekStart,
                weekEnd = view.weekEnd,
                isProvisional = view.isProvisional,
                isLastWeek = view.weekStart == currentWeekStart.minusWeeks(1),
                analyzedEntries = report.stats.analyzedTotals.frequency,
                analyzedPieces = report.stats.analyzedTotals.quantity,
                topHotspot = report.hotspots.minByOrNull { it.rank },
                trying = trying,
                hasIdeasToPick = view.canAdopt && trying.isEmpty() && report.hotspots.any { it.recommendations.isNotEmpty() },
                impacts = report.impacts,
            )
        }
    }
}

data class HomeUiState(
    val firstName: String?,
    val weekStart: LocalDate,
    val today: LocalDate,
    val online: Boolean,
    val summary: WeekSummary = WeekSummary.of(emptyList()),
    /** The newest few entries of this week. */
    val recentEntries: List<Entry> = emptyList(),
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val latestReport: LatestReportCard? = null,
    val subcategoryLabels: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
) {
    /** Nothing logged this week and no report yet: show the welcome hints. */
    val isNewUser: Boolean get() = !loading && summary.entries == 0 && latestReport == null

    fun labelOf(code: String): String = subcategoryLabels[code] ?: code

    companion object {
        const val RECENT_ENTRIES = 3

        /** Builds Home from what Room has: live week totals, the outbox, and the newest cached report. */
        fun build(
            firstName: String?,
            weekStart: LocalDate,
            today: LocalDate,
            online: Boolean,
            weekEntries: List<Entry>,
            pendingCount: Int,
            latest: ReportView?,
            labels: Map<String, String>,
        ) = HomeUiState(
            firstName = firstName,
            weekStart = weekStart,
            today = today,
            online = online,
            summary = WeekSummary.of(weekEntries),
            recentEntries = weekEntries.sortedByDescending { it.createdAt }.take(RECENT_ENTRIES),
            pendingCount = pendingCount,
            failedCount = weekEntries.count { it.syncStatus == EntrySyncStatus.FAILED },
            latestReport = latest?.let { LatestReportCard.from(it, weekStart) },
            subcategoryLabels = labels,
            loading = false,
        )
    }
}

/** Taxonomy labels for subcategory codes. A seam so Home is testable without Android. */
fun interface SubcategoryLabels {
    suspend fun labels(): Map<String, String>
}

class TaxonomySubcategoryLabels @Inject constructor(private val taxonomy: TaxonomyRepository) : SubcategoryLabels {
    override suspend fun labels(): Map<String, String> = taxonomy.taxonomy().subcategories.associate { it.code to it.label }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardModule {
    @Binds
    abstract fun subcategoryLabels(impl: TaxonomySubcategoryLabels): SubcategoryLabels
}

/**
 * Home (plan §10 Dashboard): this week's live totals, the pending-sync badge, the latest report with its top hotspot and
 * the ideas being tried, and recent entries. Built from Room, so it works offline.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val entries: EntryRepository,
    private val reports: ReportRepository,
    labels: SubcategoryLabels,
    network: NetworkStatus,
    auth: AuthService,
    private val clock: Clock,
) : ViewModel() {

    private val firstName = auth.currentUser?.displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }

    private val messages = Channel<Int>(Channel.BUFFERED)

    /** One-shot snackbar messages (string resources). */
    val messageEvents: Flow<Int> = messages.receiveAsFlow()

    private val initial = WeekCalc.currentWeekStart(clock).let { week ->
        HomeUiState(firstName = firstName, weekStart = week, today = today(), online = network.isOnline())
    }

    private val labelMap: Flow<Map<String, String>> = flow {
        emit(emptyMap())
        emit(runCatching { labels.labels() }.getOrDefault(emptyMap()))
    }

    private val week: Flow<Pair<LocalDate, List<Entry>>> =
        combine(currentWeekStartFlow(clock), entries.observeCurrentWeek()) { start, list ->
            start to list.filter { it.weekStart == start }
        }

    val state: StateFlow<HomeUiState> = combine(
        week,
        entries.observePendingCount(),
        reports.observeLatestReport(),
        labelMap,
        network.online,
    ) { (start, list), pending, latest, labelsNow, online ->
        HomeUiState.build(firstName, start, today(), online, list, pending, latest, labelsNow)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    init {
        // App-open refresh (AN-3 handoff): server reports when reachable, offline summaries otherwise. Both never throw.
        viewModelScope.launch { quietly { reports.refreshReports() } }
        viewModelScope.launch { quietly { entries.refreshCurrentWeek() } }
    }

    fun delete(id: String) {
        viewModelScope.launch { messages.send(deleteMessage { entries.delete(id) }) }
    }

    private fun today(): LocalDate = clock.instant().atZone(WeekCalc.ZONE).toLocalDate()
}

/** Background refreshes must never crash Home: they only update Room, which the screen already observes. */
private suspend fun quietly(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Offline or a server hiccup: the cached data stays on screen.
    }
}
