package app.menosan.android.feature.auth

import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.auth.SessionStore
import javax.inject.Inject

/**
 * Signs out of Firebase and clears the Credential Manager state and the session flags.
 * It does NOT touch Room: AN-4's logout must check the outbox and clear local data first (UFR3, plan §10).
 */
class SignOutAction @Inject constructor(
    private val auth: AuthService,
    private val google: GoogleSignInClient,
    private val session: SessionStore,
) {
    suspend operator fun invoke() {
        session.clear()
        auth.signOut()
        google.clearCredentialState()
    }
}
