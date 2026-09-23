package app.menosan.android.feature.auth

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.menosan.android.R
import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.data.repo.AccountRepository
import app.menosan.android.data.repo.AccountStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInPhase { Idle, SigningIn, CheckingAccount }

enum class SignInDestination { Dashboard, CreateAccount }

data class SignInUiState(
    val phase: SignInPhase = SignInPhase.Idle,
    /** Set after a few seconds of waiting on the server (Render Free can take ~60 s to wake up). */
    val slowServer: Boolean = false,
    @field:StringRes val errorRes: Int? = null,
    /** The server's request id for the failed call, to include in bug reports. */
    val requestId: String? = null,
    /** Consumed by the screen through [SignInViewModel.onNavigated]. */
    val destination: SignInDestination? = null,
) {
    val busy: Boolean get() = phase != SignInPhase.Idle
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthService,
    private val google: GoogleSignInClient,
    private val accounts: AccountRepository,
    private val signOut: SignOutAction,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    init {
        // Signed in with Google earlier, but the Menosan account was never confirmed on this device.
        if (auth.currentUser != null) checkAccount()
    }

    fun signIn(activityContext: Context) {
        if (_state.value.busy) return
        _state.update { it.copy(phase = SignInPhase.SigningIn, errorRes = null, requestId = null) }
        viewModelScope.launch {
            when (val result = google.requestIdToken(activityContext)) {
                is GoogleSignInResult.Success -> {
                    val signedIn = runCatching { auth.signInWithGoogleIdToken(result.idToken) }.isSuccess
                    if (signedIn) {
                        runAccountCheck()
                    } else {
                        fail(R.string.sign_in_error_firebase)
                    }
                }
                else -> result.failureMessage()?.let(::fail) ?: _state.update { it.copy(phase = SignInPhase.Idle) }
            }
        }
    }

    fun checkAccount() {
        if (_state.value.phase == SignInPhase.CheckingAccount) return
        viewModelScope.launch { runAccountCheck() }
    }

    fun onNavigated() = _state.update { it.copy(destination = null) }

    private suspend fun runAccountCheck() {
        _state.update { it.copy(phase = SignInPhase.CheckingAccount, errorRes = null, requestId = null, slowServer = false) }
        val slowTimer = viewModelScope.launch {
            delay(SLOW_SERVER_AFTER_MS)
            _state.update { it.copy(slowServer = true) }
        }
        val status = accounts.checkAccount()
        slowTimer.cancel()
        when (status) {
            is AccountStatus.Ready -> _state.update { SignInUiState(destination = SignInDestination.Dashboard) }
            AccountStatus.NeedsAccount -> _state.update { SignInUiState(destination = SignInDestination.CreateAccount) }
            is AccountStatus.Failed -> handleFailure(status.error)
        }
    }

    private suspend fun handleFailure(error: ApiError) {
        when {
            error is ApiError.Network -> fail(R.string.error_offline)
            error is ApiError.Http && error.code == ApiErrorCode.UNAUTHENTICATED -> {
                // The Google session isn't accepted anymore. Start over with a fresh sign-in.
                signOut()
                fail(R.string.sign_in_error_expired)
            }
            else -> fail(R.string.error_server, (error as? ApiError.Http)?.requestId)
        }
    }

    private fun fail(@StringRes message: Int, requestId: String? = null) =
        _state.update { SignInUiState(errorRes = message, requestId = requestId) }

    private companion object {
        const val SLOW_SERVER_AFTER_MS = 5_000L
    }
}
