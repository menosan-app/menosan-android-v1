package app.menosan.android.feature.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import app.menosan.android.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult

    /** No Google account on the device, or none could be used. */
    data object NoAccount : GoogleSignInResult

    /** Google Play services is missing or too old for Credential Manager (common on old phones and emulator images). */
    data object PlayServicesOutdated : GoogleSignInResult
    data object Failed : GoogleSignInResult
}

/**
 * "Sign in with Google" through Credential Manager. Returns a Google ID token for Firebase.
 * `default_web_client_id` is generated from `google-services.json` by the Google Services plugin.
 */
@Singleton
class GoogleSignInClient @Inject constructor(
    private val credentialManager: CredentialManager,
    @ApplicationContext private val appContext: Context,
) {
    /** [activityContext] must be an Activity: Credential Manager shows its account picker over it. */
    suspend fun requestIdToken(activityContext: Context): GoogleSignInResult {
        val option = GetSignInWithGoogleOption.Builder(appContext.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = credentialManager.getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleSignInResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleSignInResult.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInResult.NoAccount
        } catch (_: GetCredentialProviderConfigurationException) {
            GoogleSignInResult.PlayServicesOutdated
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager failed: ${e.type}")
            GoogleSignInResult.Failed
        } catch (e: Exception) {
            Log.w(TAG, "Google sign-in failed: ${e.javaClass.simpleName}")
            GoogleSignInResult.Failed
        }
    }

    /** Call on sign-out so the account picker shows again next time (UFR3). */
    suspend fun clearCredentialState() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't clear credential state: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "GoogleSignIn"
    }
}

/** The message to show for an unsuccessful sign-in, or null for success or when the user just cancelled. */
@androidx.annotation.StringRes
fun GoogleSignInResult.failureMessage(): Int? = when (this) {
    is GoogleSignInResult.Success, GoogleSignInResult.Cancelled -> null
    GoogleSignInResult.NoAccount -> R.string.sign_in_error_no_account
    GoogleSignInResult.PlayServicesOutdated -> R.string.sign_in_error_play_services
    GoogleSignInResult.Failed -> R.string.sign_in_error_generic
}
