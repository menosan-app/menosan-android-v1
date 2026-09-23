package app.menosan.android.feature.entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.menosan.android.R
import app.menosan.android.core.model.Entry
import app.menosan.android.core.network.ConnectivityObserver
import app.menosan.android.core.time.WeekCalc
import app.menosan.android.data.repo.EntryChangeException
import app.menosan.android.data.repo.EntryRepository
import app.menosan.android.data.repo.TaxonomyRepository
import app.menosan.android.data.repo.currentWeekStartFlow
import app.menosan.android.sync.SyncNotices
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class WeekEntries(val weekStart: LocalDate, val entries: List<Entry>)

data class AuditUiState(
    val weekStart: LocalDate,
    val today: LocalDate,
    val entries: List<Entry> = emptyList(),
    /** Up to two previous weeks kept on the phone (plan §5.7). Read-only. */
    val earlierWeeks: List<WeekEntries> = emptyList(),
    val subcategoryLabels: Map<String, String> = emptyMap(),
    val pendingCount: Int = 0,
    val online: Boolean = true,
    val revertedChanges: Int = 0,
    val loading: Boolean = true,
) {
    val summary: WeekSummary get() = WeekSummary.of(entries)
    val failedCount: Int get() = (entries + earlierWeeks.flatMap { it.entries }).count { it.syncStatus == app.menosan.android.core.model.EntrySyncStatus.FAILED }

    fun labelOf(entry: Entry): String = subcategoryLabels[entry.subcategory] ?: entry.subcategory
}

/**
 * The Audit tab (plan §10 "Waste Entries", UFR10–11): live totals and entries of the week in progress, straight from
 * Room, so it works offline. Follows the week rollover.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AuditViewModel @Inject constructor(
    private val repository: EntryRepository,
    taxonomy: TaxonomyRepository,
    connectivity: ConnectivityObserver,
    private val notices: SyncNotices,
    private val clock: Clock,
) : ViewModel() {

    private val messages = Channel<Int>(Channel.BUFFERED)

    /** One-shot snackbar messages (string resources). */
    val messageEvents: Flow<Int> = messages.receiveAsFlow()

    private val weeks: Flow<Triple<LocalDate, List<Entry>, List<WeekEntries>>> =
        currentWeekStartFlow(clock).flatMapLatest { week ->
            val previous = week.minusWeeks(1)
            val beforePrevious = week.minusWeeks(2)
            combine(
                repository.observeWeek(week),
                repository.observeWeek(previous),
                repository.observeWeek(beforePrevious),
            ) { current, prev, prev2 ->
                Triple(
                    week,
                    current,
                    listOf(WeekEntries(previous, prev), WeekEntries(beforePrevious, prev2)).filter { it.entries.isNotEmpty() },
                )
            }
        }

    private val labels: Flow<Map<String, String>> = flow {
        emit(taxonomy.taxonomy().subcategories.associate { it.code to it.label })
    }

    private val initial = WeekCalc.currentWeekStart(clock).let { week ->
        AuditUiState(weekStart = week, today = clock.instant().atZone(WeekCalc.ZONE).toLocalDate(), online = connectivity.isOnline())
    }

    val state: StateFlow<AuditUiState> = combine(
        weeks,
        labels,
        repository.observePendingCount(),
        connectivity.online,
        notices.revertedChanges,
    ) { (week, current, earlier), labels, pending, online, reverted ->
        AuditUiState(
            weekStart = week,
            today = clock.instant().atZone(WeekCalc.ZONE).toLocalDate(),
            entries = current,
            earlierWeeks = earlier,
            subcategoryLabels = labels,
            pendingCount = pending,
            online = online,
            revertedChanges = reverted,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    init {
        // Pick up entries logged on another device (plan §10 AN-1). A no-op offline; pending rows always win.
        viewModelScope.launch { repository.refreshCurrentWeek() }
    }

    fun delete(id: String) {
        viewModelScope.launch { messages.send(deleteMessage { repository.delete(id) }) }
    }

    fun dismissRevertedNotice() = notices.clearRevertedChanges()
}

/** Runs a delete and returns the snackbar message (a string resource) for it. Shared by the Audit and details screens. */
internal suspend fun deleteMessage(block: suspend () -> Unit): Int = try {
    block()
    R.string.delete_done
} catch (e: CancellationException) {
    throw e
} catch (_: EntryChangeException.WeekClosed) {
    R.string.delete_week_closed
} catch (_: EntryChangeException.NotFound) {
    R.string.details_not_found
} catch (_: Exception) {
    R.string.delete_failed
}
