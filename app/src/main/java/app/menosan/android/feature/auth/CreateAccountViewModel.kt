package app.menosan.android.feature.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.menosan.android.R
import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.network.ApiResult
import app.menosan.android.data.repo.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateAccountUiState(
    val email: String? = null,
    val consentChecked: Boolean = false,
    val submitting: Boolean = false,
    val slowServer: Boolean = false,
    @field:StringRes val errorRes: Int? = null,
    val requestId: String? = null,
    val created: Boolean = false,
) {
    val canSubmit: Boolean get() = consentChecked && !submitting
}

/** The create-account step after Google sign-in (SFR1.1–1.2): privacy notice, consent, `POST /v1/account`. */
@HiltViewModel
class CreateAccountViewModel @Inject constructor(
    auth: AuthService,
    private val accounts: AccountRepository,
    private val signOut: SignOutAction,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateAccountUiState(email = auth.currentUser?.email))
    val state: StateFlow<CreateAccountUiState> = _state.asStateFlow()

    fun onConsentChange(checked: Boolean) = _state.update { it.copy(consentChecked = checked, errorRes = null) }

    fun createAccount() {
        if (!_state.value.canSubmit) return
        _state.update { it.copy(submitting = true, slowServer = false, errorRes = null, requestId = null) }
        viewModelScope.launch {
            val slowTimer = launch {
                delay(SLOW_SERVER_AFTER_MS)
                _state.update { it.copy(slowServer = true) }
            }
            val result = accounts.createAccount()
            slowTimer.cancel()
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, created = true) }
                is ApiResult.Failure -> handleFailure(result.error)
            }
        }
    }

    /** "Use a different Google account": the app returns to sign-in when the auth state changes. */
    fun useDifferentAccount() {
        viewModelScope.launch { signOut() }
    }

    private suspend fun handleFailure(error: ApiError) {
        val message = when {
            error is ApiError.Network -> R.string.error_offline
            error is ApiError.Http && error.code == ApiErrorCode.UNAUTHENTICATED -> {
                signOut()
                R.string.sign_in_error_expired
            }
            else -> R.string.error_server
        }
        _state.update {
            it.copy(submitting = false, slowServer = false, errorRes = message, requestId = (error as? ApiError.Http)?.requestId)
        }
    }

    private companion object {
        const val SLOW_SERVER_AFTER_MS = 5_000L
    }
}
