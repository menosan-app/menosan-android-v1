package app.menosan.android.feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.menosan.android.R
import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.network.ApiResult
import app.menosan.android.core.settings.ThemeMode
import app.menosan.android.core.time.WeekCalc
import app.menosan.android.data.repo.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** What the Profile screen shows on top of the page. Kept in the view model so it survives rotation. */
sealed interface ProfileDialog {
    data object Appearance : ProfileDialog

    data object PrivacyNotice : ProfileDialog

    data object ConfirmExport : ProfileDialog

    /** Logout with entries that only exist on this phone (plan §10: warn before clearing Room). */
    data class LogoutWarning(val pendingCount: Int) : ProfileDialog

    /** Type-DELETE confirmation (team decision, DESIGN.md §5.11). */
    data class DeleteAccount(
        val typed: String = "",
        val deleting: Boolean = false,
        val error: DeleteError? = null,
    ) : ProfileDialog {
        val canDelete: Boolean get() = !deleting && isDeleteConfirmation(typed)
    }
}

enum class DeleteError { OFFLINE, SERVER, SIGN_IN_EXPIRED }

sealed interface ProfileEvent {
    /** Open the system "Save as" picker (SAF `CreateDocument`) with this suggested name. */
    data class PickExportFile(val fileName: String) : ProfileEvent

    data class Message(@param:StringRes val text: Int) : ProfileEvent

    data object LoggedOut : ProfileEvent

    data object AccountDeleted : ProfileEvent
}

data class ProfileUiState(
    val displayName: String? = null,
    val email: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val online: Boolean = true,
    val pendingCount: Int = 0,
    val dialog: ProfileDialog? = null,
    val exporting: Boolean = false,
    val loggingOut: Boolean = false,
) {
    val initials: String get() = initialsOf(displayName, email)
}

/**
 * Profile tab (plan §10 "Account Settings & Privacy"): account, Appearance, privacy notice, export (NFR16), logout
 * (UFR3), and account deletion (UFR4, NFR4).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    auth: AuthService,
    private val entries: EntryRepository,
    private val appearance: AppearanceStore,
    private val network: NetworkStatus,
    private val actions: AccountActions,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(
        ProfileUiState(
            displayName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() },
            email = auth.currentUser?.email,
            themeMode = appearance.themeMode.value,
            online = network.isOnline(),
        ),
    )

    private val events = Channel<ProfileEvent>(Channel.BUFFERED)

    /** One-shot events: messages, the export file picker, and "you're logged out". */
    val eventFlow: Flow<ProfileEvent> = events.receiveAsFlow()

    val state: StateFlow<ProfileUiState> = combine(
        local,
        appearance.themeMode,
        network.online,
        entries.observePendingCount(),
    ) { state, theme, online, pending ->
        state.copy(themeMode = theme, online = online, pendingCount = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun showDialog(dialog: ProfileDialog) = local.update { it.copy(dialog = dialog) }

    fun dismissDialog() = local.update { state ->
        // A delete in progress can't be dismissed: its result decides what happens next.
        if ((state.dialog as? ProfileDialog.DeleteAccount)?.deleting == true) state else state.copy(dialog = null)
    }

    fun setThemeMode(mode: ThemeMode) = appearance.setThemeMode(mode)

    // Export (NFR16)

    fun confirmExport() {
        local.update { it.copy(dialog = null) }
        if (!network.isOnline()) {
            send(ProfileEvent.Message(R.string.profile_export_offline))
            return
        }
        send(ProfileEvent.PickExportFile(exportFileName(clock)))
    }

    /** The document the user picked, or null when they backed out of the picker. */
    fun onExportDestination(documentUri: String?) {
        if (documentUri == null || local.value.exporting) return
        local.update { it.copy(exporting = true) }
        viewModelScope.launch {
            val result = try {
                actions.exportTo(documentUri)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ExportResult.WRITE_FAILED
            }
            local.update { it.copy(exporting = false) }
            send(
                ProfileEvent.Message(
                    when (result) {
                        ExportResult.SUCCESS -> R.string.profile_export_done
                        ExportResult.OFFLINE -> R.string.profile_export_offline
                        ExportResult.SERVER_ERROR -> R.string.profile_export_server
                        ExportResult.WRITE_FAILED -> R.string.profile_export_write_failed
                    },
                ),
            )
        }
    }

    // Logout (UFR3)

    fun logOut() {
        if (local.value.loggingOut) return
        viewModelScope.launch {
            if (entries.hasPendingChanges()) {
                val pending = entries.observePendingCount().first()
                local.update { it.copy(dialog = ProfileDialog.LogoutWarning(pending.coerceAtLeast(1))) }
            } else {
                endSession()
            }
        }
    }

    /** "Try to sync first": nudges the outbox and keeps the user signed in. */
    fun syncFirst() {
        local.update { it.copy(dialog = null) }
        entries.requestSync()
        send(ProfileEvent.Message(if (network.isOnline()) R.string.profile_logout_syncing else R.string.profile_logout_sync_offline))
    }

    /** "Log out anyway": the user accepted that unsynced entries are lost. */
    fun logOutAnyway() {
        local.update { it.copy(dialog = null) }
        viewModelScope.launch { endSession() }
    }

    private suspend fun endSession() {
        local.update { it.copy(loggingOut = true) }
        try {
            actions.endSession()
            send(ProfileEvent.LoggedOut)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            local.update { it.copy(loggingOut = false) }
            send(ProfileEvent.Message(R.string.profile_logout_failed))
        }
    }

    // Account deletion (UFR4, NFR4)

    fun onDeleteTextChange(text: String) = updateDelete { it.copy(typed = text.take(DELETE_INPUT_MAX), error = null) }

    fun confirmDelete() {
        val dialog = local.value.dialog as? ProfileDialog.DeleteAccount ?: return
        if (!dialog.canDelete) return
        if (!network.isOnline()) {
            updateDelete { it.copy(error = DeleteError.OFFLINE) }
            return
        }
        updateDelete { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            when (val result = deleteWithRetry()) {
                is ApiResult.Success -> {
                    // The server data and the Firebase user are gone: wipe this phone and sign out (contract §6.7).
                    try {
                        actions.endSession()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Nothing more to do: the account no longer exists, so the next start asks to sign in again.
                    }
                    local.update { it.copy(dialog = null) }
                    send(ProfileEvent.AccountDeleted)
                }
                is ApiResult.Failure -> updateDelete { it.copy(deleting = false, error = result.error.toDeleteError()) }
            }
        }
    }

    /** `500` means the data is gone but the Firebase user isn't yet; the contract says to retry (§6.7). */
    private suspend fun deleteWithRetry(): ApiResult<Unit> {
        var attempt = 1
        while (true) {
            val result = actions.deleteAccount()
            val error = (result as? ApiResult.Failure)?.error
            if (error !is ApiError.Http || error.status < 500 || attempt >= DELETE_ATTEMPTS) return result
            delay(DELETE_RETRY_DELAY_MS * attempt)
            attempt++
        }
    }

    private fun updateDelete(transform: (ProfileDialog.DeleteAccount) -> ProfileDialog.DeleteAccount) = local.update { state ->
        val dialog = state.dialog as? ProfileDialog.DeleteAccount ?: return@update state
        state.copy(dialog = transform(dialog))
    }

    private fun send(event: ProfileEvent) {
        events.trySend(event)
    }

    companion object {
        const val DELETE_WORD = "DELETE"
        const val DELETE_ATTEMPTS = 3
        const val DELETE_RETRY_DELAY_MS = 1_500L
        private const val DELETE_INPUT_MAX = 20
    }
}

/** True when the user typed DELETE (surrounding spaces and letter case don't matter). */
fun isDeleteConfirmation(text: String): Boolean = text.trim().equals(ProfileViewModel.DELETE_WORD, ignoreCase = true)

/** `menosan-export-YYYY-MM-DD.json`, with today's date in Manila (plan §10, contract §6.6). */
fun exportFileName(clock: Clock): String = "menosan-export-${clock.instant().atZone(WeekCalc.ZONE).toLocalDate()}.json"

/** "LC" for "Liza Cabaraban", "L" for "Liza", the email's first letter without a name, or "" when nothing is known. */
fun initialsOf(displayName: String?, email: String?): String {
    val words = displayName?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
    val letters = when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}"
        words.size == 1 -> words.first().first().toString()
        else -> email?.trim()?.firstOrNull()?.toString().orEmpty()
    }
    return letters.uppercase()
}

private fun ApiError.toDeleteError(): DeleteError = when {
    this is ApiError.Network -> DeleteError.OFFLINE
    this is ApiError.Http && code == ApiErrorCode.UNAUTHENTICATED -> DeleteError.SIGN_IN_EXPIRED
    else -> DeleteError.SERVER
}
