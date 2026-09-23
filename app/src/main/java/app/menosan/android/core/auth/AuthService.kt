package app.menosan.android.core.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in Firebase user, reduced to what the app needs. No tokens here. */
data class AuthUser(val uid: String, val email: String?, val displayName: String?)

/** Firebase Authentication (Google provider only, NFR5). Credential Manager lives in `feature/auth`. */
interface AuthService : IdTokenProvider {
    val currentUser: AuthUser?

    /** Emits on every sign-in and sign-out. */
    val authState: Flow<AuthUser?>

    /** Signs in to Firebase with a Google ID token from Credential Manager. */
    suspend fun signInWithGoogleIdToken(googleIdToken: String): AuthUser

    /** Signs out of Firebase only. Also clear the Credential Manager state (see `GoogleSignInClient`). */
    fun signOut()
}

@Singleton
class FirebaseAuthService @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthService {

    override val currentUser: AuthUser? get() = auth.currentUser?.toAuthUser()

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogleIdToken(googleIdToken: String): AuthUser {
        val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
        val user = auth.signInWithCredential(credential).await().user
            ?: error("Firebase returned no user")
        return user.toAuthUser()
    }

    override fun signOut() = auth.signOut()

    override fun idToken(forceRefresh: Boolean): String? {
        val user = auth.currentUser ?: return null
        return try {
            Tasks.await(user.getIdToken(forceRefresh), TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS).token
        } catch (_: Exception) {
            null
        }
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthUser() = AuthUser(uid, email, displayName)

    private companion object {
        const val TOKEN_TIMEOUT_SECONDS = 20L
    }
}
