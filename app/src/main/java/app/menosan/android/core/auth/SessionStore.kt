package app.menosan.android.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which Firebase user already has a confirmed Menosan account, so the app can open
 * straight to the dashboard (and log offline) without waiting for `GET /v1/me` on every start.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAccountConfirmed(uid: String): Boolean = prefs.getString(KEY_CONFIRMED_UID, null) == uid

    fun markAccountConfirmed(uid: String) = prefs.edit { putString(KEY_CONFIRMED_UID, uid) }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val PREFS = "menosan_session"
        const val KEY_CONFIRMED_UID = "confirmed_account_uid"
    }
}
