package app.menosan.android.feature.entries

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.menosan.android.R
import app.menosan.android.core.model.Entry
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.EntrySyncStatus
import app.menosan.android.core.ui.components.BannerTone
import app.menosan.android.core.ui.components.MessageBanner
import app.menosan.android.core.ui.components.ScreenHeader
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.data.repo.EntryRepository
import app.menosan.android.data.repo.TaxonomyRepository
import app.menosan.android.feature.logging.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/** Entry details (AN-1, mockup `Waste Entry Details`). */
@Serializable
data class EntryDetailsRoute(val entryId: String)

sealed interface EntryDetailsUiState {
    data object Loading : EntryDetailsUiState
    data object NotFound : EntryDetailsUiState
    data class Loaded(val entry: Entry, val subcategoryLabel: String) : EntryDetailsUiState
}

sealed interface EntryDetailsEvent {
    data object Deleted : EntryDetailsEvent
    data class Message(val text: Int) : EntryDetailsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntryDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EntryRepository,
    taxonomy: TaxonomyRepository,
) : ViewModel() {
    private val entryId = savedStateHandle.toRoute<EntryDetailsRoute>().entryId
    private val events = Channel<EntryDetailsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EntryDetailsEvent> = events.receiveAsFlow()

    /** Follows the entry's week, so the sync chip updates live while the screen is open. */
    val state: StateFlow<EntryDetailsUiState> = flow { emit(repository.get(entryId)) }
        .flatMapLatest { first ->
            if (first == null) flowOf(null) else repository.observeWeek(first.weekStart).map { list -> list.firstOrNull { it.id == entryId } }
        }
        .map { entry ->
            if (entry == null) {
                EntryDetailsUiState.NotFound
            } else {
                val label = taxonomy.taxonomy().subcategory(entry.subcategory)?.label ?: entry.subcategory
                EntryDetailsUiState.Loaded(entry, label)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryDetailsUiState.Loading)

    fun delete() {
        viewModelScope.launch {
            when (val message = deleteMessage { repository.delete(entryId) }) {
                R.string.delete_done -> events.send(EntryDetailsEvent.Deleted)
                else -> events.send(EntryDetailsEvent.Message(message))
            }
        }
    }
}

@Composable
fun EntryDetailsRouteScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: EntryDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                EntryDetailsEvent.Deleted -> {
                    deleting = true
                    onDeleted()
                }
                is EntryDetailsEvent.Message -> snackbar.showSnackbar(resources.getString(event.text))
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        EntryDetailsScreen(
            state = if (deleting) EntryDetailsUiState.Loading else state,
            onBack = onBack,
            onEdit = onEdit,
            onDelete = viewModel::delete,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

@Composable
fun EntryDetailsScreen(
    state: EntryDetailsUiState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.details_title), onBack = onBack)
        when (state) {
            EntryDetailsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            EntryDetailsUiState.NotFound -> Text(
                stringResource(R.string.details_not_found),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
            is EntryDetailsUiState.Loaded -> Details(state, onEdit = { onEdit(state.entry.id) }, onDelete = { confirmDelete = true })
        }
    }
    if (confirmDelete) {
        DeleteEntryDialog(
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Details(state: EntryDetailsUiState.Loaded, onEdit: () -> Unit, onDelete: () -> Unit) {
    val entry = state.entry
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            color = MenosanTheme.colors.card,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryBadge(entry.category)
                    Column {
                        Text(state.subcategoryLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            stringResource(entry.category.labelRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DetailRow(stringResource(R.string.details_item_name), entry.name)
                DetailRow(stringResource(R.string.details_quantity), pluralStringResource(R.plurals.audit_pieces, entry.quantity, entry.quantity))
                DetailRow(stringResource(R.string.details_date), EntryFormats.date(entry.createdAt))
                DetailRow(stringResource(R.string.details_time), EntryFormats.time(entry.createdAt))
                DetailRow(
                    stringResource(R.string.details_method),
                    stringResource(if (entry.source == EntrySource.PHOTO) R.string.details_method_photo else R.string.details_method_manual),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.details_sync), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    SyncChip(entry.syncStatus)
                }
            }
        }

        if (entry.syncStatus == EntrySyncStatus.FAILED && entry.lastError != null) {
            MessageBanner(title = stringResource(R.string.details_sync_error_title), text = entry.lastError, tone = BannerTone.Error)
        }

        if (entry.editable) {
            MessageBanner(
                title = stringResource(R.string.details_editable_until_title, EntryFormats.editableUntil(entry.weekStart)),
                text = stringResource(R.string.details_editable_until_body),
                icon = Icons.Outlined.Schedule,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.details_edit), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = onDelete,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.details_delete), style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            MessageBanner(
                title = stringResource(R.string.details_read_only_title),
                text = stringResource(R.string.details_read_only_body),
                icon = Icons.Outlined.Lock,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
