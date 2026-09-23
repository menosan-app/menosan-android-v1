package app.menosan.android.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flushes the entry outbox (plan §10 AN-1). WorkManager persists the request, so pending entries sync after an app
 * kill, restart, or reboot (NFR7). Only runs with a network connection, with exponential backoff on failures.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: EntrySyncEngine,
    private val afterSyncActions: Set<@JvmSuppressWildcards AfterSyncAction>,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = engine.pushOutbox()
        val stats = outcome.stats
        // Counts only: never entry contents, ids, or tokens (plan §14).
        Log.i(TAG, "sync ${outcome::class.simpleName}: sent=${stats.sent} synced=${stats.synced} reverted=${stats.reverted} " +
            "failed=${stats.failed} conflicts=${stats.conflicts} retryable=${stats.retryable} attempt=$runAttemptCount")
        return when (outcome) {
            is PushOutcome.Done -> {
                runCatching { engine.pruneOldEntries() }
                afterSyncActions.forEach { action ->
                    try {
                        action.afterSync(stats.sent)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "after-sync action failed: ${e.javaClass.simpleName}")
                    }
                }
                Result.success()
            }
            is PushOutcome.RetryLater -> Result.retry()
            is PushOutcome.Stopped -> Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "entry-sync"
        private const val TAG = "MenosanSync"
    }
}

/**
 * Enqueues [SyncWorker] as unique work. `REPLACE` restarts a waiting or running sync right away, so a new change
 * doesn't wait out an earlier backoff; that's safe because every sync item is idempotent on the server (contract §6.5)
 * and results are applied in a transaction.
 */
@Singleton
class WorkManagerSyncRequester @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncRequester {
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SyncWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
