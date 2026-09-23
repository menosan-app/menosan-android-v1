package app.menosan.android.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.menosan.android.core.network.ApiError
import app.menosan.android.data.repo.AdoptionResult
import app.menosan.android.data.repo.RefreshResult
import app.menosan.android.data.repo.ReportRepository
import app.menosan.android.data.repo.ReportView
import app.menosan.android.data.repo.TaxonomyRepository
import app.menosan.android.navigation.ReportRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReportUiState(
    val weekStart: LocalDate?,
    val view: ReportView? = null,
    /** Category and subcategory code → taxonomy label (codes never collide). */
    val labels: Map<String, String> = emptyMap(),
    /** The cache has been read at least once. */
    val loaded: Boolean = false,
    val refreshing: Boolean = true,
    val notFound: Boolean = false,
    val problem: ReportProblem? = null,
    /** Intervention ids with an adopt or un-adopt request in flight. */
    val pendingAdoptions: Set<String> = emptySet(),
) {
    fun label(code: String): String = labels[code] ?: code
}

/** One-shot feedback after Adopt / Un-adopt. */
enum class ReportMessage { Adopted, Unadopted, WindowClosed, Offline, Failed }

@HiltViewModel
class ReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReportRepository,
    private val taxonomy: TaxonomyRepository,
) : ViewModel() {
    private val weekStart: LocalDate? =
        runCatching { LocalDate.parse(savedStateHandle.toRoute<ReportRoute>().weekStart) }.getOrNull()

    private val status = MutableStateFlow(ReportUiState(weekStart = weekStart, refreshing = weekStart != null, notFound = weekStart == null))
    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val messageChannel = Channel<ReportMessage>(Channel.BUFFERED)

    val messages: Flow<ReportMessage> = messageChannel.receiveAsFlow()

    private val report: Flow<ReportView?> = weekStart?.let { repository.observeReport(it) } ?: flowOf(null)

    val state: StateFlow<ReportUiState> = combine(report, status, labels) { view, s, l ->
        s.copy(view = view, labels = l, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), status.value)

    init {
        viewModelScope.launch {
            val tax = taxonomy.taxonomy()
            labels.value = tax.categories.associate { it.code.name to it.label } + tax.subcategories.associate { it.code to it.label }
        }
        refresh()
    }

    fun refresh() {
        val week = weekStart ?: return
        status.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val result = repository.refreshReport(week)
            status.update {
                it.copy(
                    refreshing = false,
                    notFound = result == RefreshResult.NotFound,
                    problem = (result as? RefreshResult.Failure)?.error?.toProblem(),
                )
            }
        }
    }

    fun setAdopted(interventionId: String, adopted: Boolean) {
        val week = weekStart ?: return
        if (interventionId in status.value.pendingAdoptions) return
        status.update { it.copy(pendingAdoptions = it.pendingAdoptions + interventionId) }
        viewModelScope.launch {
            val result = repository.setAdopted(week, interventionId, adopted)
            status.update { it.copy(pendingAdoptions = it.pendingAdoptions - interventionId) }
            val message = when (result) {
                AdoptionResult.Success -> if (adopted) ReportMessage.Adopted else ReportMessage.Unadopted
                AdoptionResult.WindowClosed -> ReportMessage.WindowClosed
                AdoptionResult.NotAvailable -> ReportMessage.Failed
                is AdoptionResult.Failure ->
                    if (result.error is ApiError.Network) ReportMessage.Offline else ReportMessage.Failed
            }
            messageChannel.send(message)
        }
    }
}
