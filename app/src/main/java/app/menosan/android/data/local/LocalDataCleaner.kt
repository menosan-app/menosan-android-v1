package app.menosan.android.data.local

import android.content.Context
import androidx.work.WorkManager
import app.menosan.android.core.auth.SessionStore
import app.menosan.android.sync.SyncNotices
import app.menosan.android.sync.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Wipes everything this account left on the device: entries (including the outbox), cached reports, sync notices,
 * and session flags. Used by logout (only after the outbox is empty or the user accepted losing it) and after account
 * deletion (AN-4, plan §10). The taxonomy falls back to the bundled asset.
 */
class LocalDataCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MenosanDatabase,
    private val session: SessionStore,
    private val syncNotices: SyncNotices,
) {
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        // Stop a pending or running sync first, so it can't write rows back after the wipe.
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.UNIQUE_NAME)
        db.clearAllTables()
        syncNotices.clearRevertedChanges()
        session.clear()
    }
}
