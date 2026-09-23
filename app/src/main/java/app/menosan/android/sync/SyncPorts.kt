package app.menosan.android.sync

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import app.menosan.android.core.network.ApiResult
import app.menosan.android.core.network.safeApiCall
import app.menosan.android.data.local.MenosanDatabase
import app.menosan.android.data.remote.MenosanApi
import app.menosan.android.data.remote.dto.EntryListDto
import app.menosan.android.data.remote.dto.SyncRequest
import app.menosan.android.data.remote.dto.SyncResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Small seams around Android and the network, so the outbox and merge logic in `EntrySyncEngine` is JVM-testable.

/** The two entry endpoints the sync needs (contract §6.2, §6.5). Never throws. */
interface EntryRemote {
    suspend fun sync(request: SyncRequest): ApiResult<SyncResponse>

    /** `GET /v1/entries` for the server's current week. */
    suspend fun currentWeekEntries(): ApiResult<EntryListDto>
}

class ApiEntryRemote @Inject constructor(private val api: MenosanApi) : EntryRemote {
    override suspend fun sync(request: SyncRequest): ApiResult<SyncResponse> = safeApiCall { api.syncEntries(request) }

    override suspend fun currentWeekEntries(): ApiResult<EntryListDto> = safeApiCall { api.entries() }
}

/** Runs [block] in one database transaction. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner @Inject constructor(private val db: MenosanDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}

/** Enqueues the background sync. Implemented with WorkManager by `WorkManagerSyncRequester`. */
fun interface SyncRequester {
    fun requestSync()
}

/**
 * One-off notices from the sync that the user should see once, even if it ran in the background: how many local
 * edits or deletes were undone because their week had closed before they reached the server (contract §6.5).
 */
interface SyncNotices {
    val revertedChanges: StateFlow<Int>

    fun addRevertedChanges(count: Int)

    fun clearRevertedChanges()
}

@Singleton
class PrefsSyncNotices @Inject constructor(@ApplicationContext context: Context) : SyncNotices {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val reverted = MutableStateFlow(prefs.getInt(KEY_REVERTED, 0))

    override val revertedChanges: StateFlow<Int> = reverted.asStateFlow()

    @Synchronized
    override fun addRevertedChanges(count: Int) {
        if (count <= 0) return
        val total = reverted.value + count
        prefs.edit { putInt(KEY_REVERTED, total) }
        reverted.value = total
    }

    @Synchronized
    override fun clearRevertedChanges() {
        prefs.edit { remove(KEY_REVERTED) }
        reverted.value = 0
    }

    private companion object {
        const val PREFS = "menosan_sync"
        const val KEY_REVERTED = "reverted_changes"
    }
}

/**
 * Runs after a sync pass has finished without needing a retry (the outbox is flushed, apart from entries that
 * "couldn't sync"). Other workstreams add one with `@IntoSet` in their own Hilt module, e.g. AN-3 refreshing the
 * latest report after late entries reached the server (plan §5.7). [sentItems] is how many changes were sent.
 * Must not throw; failures are ignored.
 */
fun interface AfterSyncAction {
    suspend fun afterSync(sentItems: Int)
}
