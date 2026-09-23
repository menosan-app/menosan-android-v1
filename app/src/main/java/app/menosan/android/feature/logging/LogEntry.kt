package app.menosan.android.feature.logging

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.menosan.android.R
import app.menosan.android.core.model.EntryDraft
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.Taxonomy
import app.menosan.android.core.network.ConnectivityObserver
import app.menosan.android.core.ui.components.BannerTone
import app.menosan.android.core.ui.components.MessageBanner
import app.menosan.android.core.ui.components.ScreenHeader
import app.menosan.android.data.repo.EntryChangeException
import app.menosan.android.data.repo.EntryRepository
import app.menosan.android.data.repo.TaxonomyRepository
import app.menosan.android.navigation.LogManualRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogEntryUiState(
    val isEdit: Boolean,
    val loading: Boolean = true,
    val taxonomy: Taxonomy? = null,
    val form: EntryFormState = EntryFormState(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val online: Boolean = true,
    /** A blocking problem (entry gone, or its week closed): the form is disabled. */
    val blockingError: Int? = null,
    /** A non-blocking save error shown above the button. */
    val saveError: Int? = null,
) {
    val canSave: Boolean get() = !loading && !saving && blockingError == null && taxonomy != null
}

sealed interface LogEntryEvent {
    data class Saved(val message: Int) : LogEntryEvent
}

/**
 * Manual logging (`LogManualRoute()`) and editing (`LogManualRoute(entryId)`), plan §10 "Waste Logging" (UFR5–6,
 * UFR11). Saves go to Room through [EntryRepository], so this works offline.
 */
@HiltViewModel
class LogEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EntryRepository,
    private val taxonomyRepository: TaxonomyRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {
    private val entryId: String? = savedStateHandle.toRoute<LogManualRoute>().entryId
    private var source = EntrySource.MANUAL

    private val _state = MutableStateFlow(LogEntryUiState(isEdit = entryId != null, online = connectivity.isOnline()))
    val state: StateFlow<LogEntryUiState> = _state.asStateFlow()

    private val events = Channel<LogEntryEvent>(Channel.BUFFERED)
    val eventFlow: Flow<LogEntryEvent> = events.receiveAsFlow()

    init {
        viewModelScope.launch { connectivity.online.collect { online -> _state.update { it.copy(online = online) } } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val taxonomy = taxonomyRepository.taxonomy()
        if (entryId == null) {
            _state.update { it.copy(loading = false, taxonomy = taxonomy) }
            return
        }
        val entry = repository.get(entryId)
        if (entry == null) {
            _state.update { it.copy(loading = false, taxonomy = taxonomy, blockingError = R.string.log_error_not_found) }
            return
        }
        source = entry.source
        val form = EntryFormState.from(EntryDraft(entry.name, entry.subcategory, entry.quantity, entry.source), taxonomy)
        _state.update {
            it.copy(
                loading = false,
                taxonomy = taxonomy,
                form = form,
                blockingError = if (entry.editable) null else R.string.log_error_week_closed,
            )
        }
    }

    fun onFormChange(form: EntryFormState) = _state.update { it.copy(form = form, saveError = null) }

    fun save() {
        val current = _state.value
        val taxonomy = current.taxonomy ?: return
        if (!current.canSave) return
        val draft = current.form.toDraft(taxonomy, source)
        if (draft == null) {
            _state.update { it.copy(showErrors = true, saveError = R.string.log_error_invalid) }
            return
        }
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                if (entryId == null) repository.create(draft) else repository.update(entryId, draft)
                val message = when {
                    entryId != null -> R.string.log_saved_edit
                    _state.value.online -> R.string.log_saved
                    else -> R.string.log_saved_offline
                }
                events.send(LogEntryEvent.Saved(message))
            } catch (e: CancellationException) {
                throw e
            } catch (_: EntryChangeException.WeekClosed) {
                _state.update { it.copy(blockingError = R.string.log_error_week_closed) }
            } catch (_: EntryChangeException.NotFound) {
                _state.update { it.copy(blockingError = R.string.log_error_not_found) }
            } catch (_: EntryChangeException.Invalid) {
                _state.update { it.copy(showErrors = true, saveError = R.string.log_error_invalid) }
            } catch (_: Exception) {
                _state.update { it.copy(saveError = R.string.log_error_generic) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

@Composable
fun LogEntryRoute(onDone: () -> Unit, viewModel: LogEntryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                // A toast survives the navigation back to wherever the user came from (Home, Audit, …).
                is LogEntryEvent.Saved -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    onDone()
                }
            }
        }
    }
    LogEntryScreen(state, onBack = onDone, onFormChange = viewModel::onFormChange, onSave = viewModel::save)
}

/** Mockups `Manual Waste Entry` and `Entry Correction`. */
@Composable
fun LogEntryScreen(
    state: LogEntryUiState,
    onBack: () -> Unit,
    onFormChange: (EntryFormState) -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        ScreenHeader(
            title = stringResource(if (state.isEdit) R.string.log_title_edit else R.string.log_title_new),
            onBack = onBack,
        )
        if (state.loading || state.taxonomy == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.online) {
                MessageBanner(text = stringResource(R.string.log_offline_banner), icon = Icons.Outlined.CloudOff)
            }
            state.blockingError?.let { MessageBanner(text = stringResource(it), tone = BannerTone.Error) }
            EntryFormFields(
                state = state.form,
                taxonomy = state.taxonomy,
                onChange = onFormChange,
                showErrors = state.showErrors,
                enabled = state.blockingError == null && !state.saving,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.saveError?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onSave,
                enabled = state.canSave,
                shape = MaterialTheme.shapes.small,
                // Brand fill: Moss in both modes (DESIGN.md §2).
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.heightIn(max = 20.dp))
                } else {
                    Text(
                        stringResource(if (state.isEdit) R.string.log_save_changes else R.string.log_save),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
            if (state.isEdit) {
                OutlinedButton(
                    onClick = onBack,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.log_cancel), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
