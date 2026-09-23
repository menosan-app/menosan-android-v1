package app.menosan.android.feature.auth

import android.content.Context
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

enum class CreateAccountPhase { Idle, SigningIn, Creating }

enum class CreateAccountOutcome {
    /** `201`: a new account. Show the "You're in" screen. */
    Created,

    /** `200`: this Google account already had one. Go straight to the dashboard. */
    AlreadyExisted,
}

data class CreateAccountUiState(
    /** Set when already signed in with Google (e.g. sent here by Log in after `404 ACCOUNT_NOT_FOUND`). */
    val signedInEmail: String? = null,
    val consentChecked: Boolean = false,
    val phase: CreateAccountPhase = CreateAccountPhase.Idle,
    val slowServer: Boolean = false,
    @field:StringRes val errorRes: Int? = null,
    val requestId: String? = null,
    val outcome: CreateAccountOutcome? = null,
) {
    val busy: Boolean get() = phase != CreateAccountPhase.Idle
    val canSubmit: Boolean get() = consentChecked && !busy
}

/**
 * Create account (mockup `Create Account`, SFR1.1–1.2): the privacy notice and consent come first, then Google
 * sign-in if needed, then `POST /v1/account {consent: true}`.
 */
@HiltViewModel
class CreateAccountViewModel @Inject constructor(
    private val auth: AuthService,
    private val google: GoogleSignInClient,
    private val accounts: AccountRepository,
    private val signOut: SignOutAction,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateAccountUiState(signedInEmail = auth.currentUser?.email))
    val state: StateFlow<CreateAccountUiState> = _state.asStateFlow()

    fun onConsentChange(checked: Boolean) = _state.update { it.copy(consentChecked = checked, errorRes = null) }

    /** "Continue with Google" when signed out, "Create my account" when already signed in. */
    fun onContinue(activityContext: Context) {
        if (!_state.value.canSubmit) return
        _state.update { it.copy(errorRes = null, requestId = null) }
        viewModelScope.launch {
            if (auth.currentUser == null) {
                _state.update { it.copy(phase = CreateAccountPhase.SigningIn) }
                val result = google.requestIdToken(activityContext)
                if (result !is GoogleSignInResult.Success) {
                    _state.update { it.copy(phase = CreateAccountPhase.Idle, errorRes = result.failureMessage()) }
                    return@launch
                }
                val user = runCatching { auth.signInWithGoogleIdToken(result.idToken) }.getOrNull()
                if (user == null) {
                    _state.update { it.copy(phase = CreateAccountPhase.Idle, errorRes = R.string.sign_in_error_firebase) }
                    return@launch
                }
                _state.update { it.copy(signedInEmail = user.email) }
            }
            create()
        }
    }

    fun onOutcomeHandled() = _state.update { it.copy(outcome = null) }

    /** "Use a different Google account". */
    fun useDifferentAccount() {
        viewModelScope.launch {
            signOut()
            _state.update { it.copy(signedInEmail = null) }
        }
    }

    private suspend fun create() {
        _state.update { it.copy(phase = CreateAccountPhase.Creating, slowServer = false) }
        val slowTimer = viewModelScope.launch {
            delay(SLOW_SERVER_AFTER_MS)
            _state.update { it.copy(slowServer = true) }
        }
        val result = accounts.createAccount()
        slowTimer.cancel()
        when (result) {
            is ApiResult.Success -> _state.update {
                it.copy(
                    phase = CreateAccountPhase.Idle,
                    outcome = if (result.status == 201) CreateAccountOutcome.Created else CreateAccountOutcome.AlreadyExisted,
                )
            }
            is ApiResult.Failure -> handleFailure(result.error)
        }
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
            it.copy(
                phase = CreateAccountPhase.Idle,
                slowServer = false,
                signedInEmail = auth.currentUser?.email,
                errorRes = message,
                requestId = (error as? ApiError.Http)?.requestId,
            )
        }
    }

    private companion object {
        const val SLOW_SERVER_AFTER_MS = 5_000L
    }
}
