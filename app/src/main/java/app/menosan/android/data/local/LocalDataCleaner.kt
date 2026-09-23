package app.menosan.android.data.local

import app.menosan.android.core.auth.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Wipes everything this account left on the device: entries (including the outbox), cached reports, and session
 * flags. Used by logout (only after the outbox is empty or the user accepted losing it) and after account deletion
 * (AN-4, plan §10). The taxonomy falls back to the bundled asset.
 */
class LocalDataCleaner @Inject constructor(
    private val db: MenosanDatabase,
    private val session: SessionStore,
) {
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        session.clear()
    }
}
