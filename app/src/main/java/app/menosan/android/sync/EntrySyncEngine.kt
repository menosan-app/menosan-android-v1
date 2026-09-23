package app.menosan.android.sync

import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.network.ApiResult
import app.menosan.android.core.time.WeekCalc
import app.menosan.android.data.local.EntryDao
import app.menosan.android.data.local.EntryEntity
import app.menosan.android.data.local.SyncState
import app.menosan.android.data.remote.dto.EntryDto
import app.menosan.android.data.remote.dto.SyncOp
import app.menosan.android.data.remote.dto.SyncRequest
import app.menosan.android.data.remote.dto.SyncResultDto
import app.menosan.android.data.remote.dto.SyncStatus
import app.menosan.android.data.remote.dto.SyncUpsertDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What one outbox push did. Counts only; never entry contents (plan §14). */
data class PushStats(
    /** Changes sent to the server. */
    val sent: Int = 0,
    /** Answered `OK` and applied locally. */
    val synced: Int = 0,
    /** Local edits or deletes undone because their week had closed (`WEEK_CLOSED`). */
    val reverted: Int = 0,
    /** Final errors (`INVALID`, `INVALID_TIMESTAMP`, `CONFLICT`): kept locally as "Couldn't sync". */
    val failed: Int = 0,
    val conflicts: Int = 0,
    /** `ERROR`, unknown statuses, or missing results: kept and retried later. */
    val retryable: Int = 0,
) {
    operator fun plus(other: PushStats) = PushStats(
        sent + other.sent, synced + other.synced, reverted + other.reverted,
        failed + other.failed, conflicts + other.conflicts, retryable + other.retryable,
    )
}

sealed interface PushOutcome {
    val stats: PushStats

    /** Every change that could be answered was answered. Nothing to retry. */
    data class Done(override val stats: PushStats) : PushOutcome

    /** Offline, server trouble, or per-item `ERROR`: keep the outbox and try again with backoff. */
    data class RetryLater(override val stats: PushStats) : PushOutcome

    /**
     * Retrying on a timer won't help (signed out, or the server refused the whole request). The outbox is kept and
     * the next trigger (app start, sign-in, a new change) tries again.
     */
    data class Stopped(override val stats: PushStats, val reason: String) : PushOutcome
}

/**
 * The offline outbox and the current-week merge (plan §10 AN-1, contract §6.5). Room is the source of truth: this
 * class only sends pending rows and applies the server's per-item answers. It never deletes a pending change
 * without a final answer from the server, so nothing logged offline is lost (NFR7, NFR8).
 *
 * Pushes and pulls run one at a time (a [Mutex]). Results are applied in one transaction per batch, and only to rows
 * the user hasn't changed while the request was in flight; a newer local change is sent by the next pass instead.
 */
@Singleton
class EntrySyncEngine @Inject constructor(
    private val dao: EntryDao,
    private val remote: EntryRemote,
    private val tx: TransactionRunner,
    private val notices: SyncNotices,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Sends the outbox through `POST /v1/entries/sync` in batches of at most [MAX_BATCH] items. */
    suspend fun pushOutbox(): PushOutcome = mutex.withLock {
        var stats = PushStats()
        // Snapshots already sent in this run. A row that is still pending and unchanged (an `ERROR` answer) isn't
        // resent until the next run; a row the user changed meanwhile is a new snapshot and goes in the next pass.
        val attempted = HashSet<EntryEntity>()
        repeat(MAX_PASSES) {
            val outbox = dao.getOutbox().filterNot { it in attempted }
            if (outbox.isEmpty()) return@withLock finish(stats)
            for (batch in outbox.chunked(MAX_BATCH)) {
                attempted += batch
                when (val result = sendBatch(batch)) {
                    is BatchResult.Applied -> stats += result.stats
                    is BatchResult.Failed -> return@withLock result.outcome(stats)
                }
            }
        }
        finish(stats)
    }

    /**
     * Pulls `GET /v1/entries` for the server's current week and merges it: the server wins for synced rows, local
     * wins for pending rows, and synced rows the server no longer has (deleted on another device) are removed.
     * Returns false when offline or on any error (nothing changes then).
     */
    suspend fun pullCurrentWeek(): Boolean = mutex.withLock {
        val list = when (val result = remote.currentWeekEntries()) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return@withLock false
        }
        tx.run {
            val serverIds = HashSet<String>(list.entries.size)
            for (dto in list.entries) {
                serverIds += dto.id
                val local = dao.getById(dto.id)
                if (local == null || local.syncState == SyncState.SYNCED) dao.upsert(dto.toEntity())
            }
            dao.getWeekIncludingDeleted(list.weekStart)
                .filter { it.syncState == SyncState.SYNCED && it.id !in serverIds }
                .forEach { dao.deleteById(it.id) }
        }
        true
    }

    /**
     * Retention (plan §5.7): keeps the current week and the two previous ones. Older synced rows are dropped;
     * pending rows are never pruned.
     */
    suspend fun pruneOldEntries(): Int = dao.pruneSyncedBefore(oldestKeptWeekStart())

    fun oldestKeptWeekStart(): LocalDate = WeekCalc.currentWeekStart(clock).minusWeeks(KEPT_PREVIOUS_WEEKS)

    private fun finish(stats: PushStats): PushOutcome =
        if (stats.retryable > 0) PushOutcome.RetryLater(stats) else PushOutcome.Done(stats)

    private sealed interface BatchResult {
        data class Applied(val stats: PushStats) : BatchResult
        data class Failed(val outcome: (PushStats) -> PushOutcome) : BatchResult
    }

    private suspend fun sendBatch(batch: List<EntryEntity>): BatchResult {
        // The server answers upserts first, then deletes, each in request order (contract §6.5).
        val upserts = batch.filter { it.syncState != SyncState.PENDING_DELETE }
        val deletes = batch.filter { it.syncState == SyncState.PENDING_DELETE }
        val request = SyncRequest(upserts = upserts.map { it.toUpsertDto() }, deletes = deletes.map { it.id })
        val response = when (val result = remote.sync(request)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return BatchResult.Failed(failureOutcome(result.error))
        }
        val ordered = upserts + deletes
        val stats = tx.run {
            var stats = PushStats(sent = ordered.size)
            ordered.forEachIndexed { index, sent ->
                val op = if (sent.syncState == SyncState.PENDING_DELETE) SyncOp.DELETE else SyncOp.UPSERT
                val answer = response.results.getOrNull(index)?.takeIf { it.id == sent.id && it.op == op }
                    ?: response.results.firstOrNull { it.id == sent.id && it.op == op }
                stats += apply(sent, answer)
            }
            stats
        }
        notices.addRevertedChanges(stats.reverted)
        return BatchResult.Applied(stats)
    }

    /** Applies one answer. Runs inside the batch transaction. */
    private suspend fun apply(sent: EntryEntity, answer: SyncResultDto?): PushStats {
        val current = dao.getById(sent.id)
        // The row is gone (e.g. local data was cleared on logout): nothing to update.
            ?: return if (answer == null || answer.status.isRetryable()) PushStats(retryable = 1) else PushStats()
        val unchanged = current == sent
        val isDelete = sent.syncState == SyncState.PENDING_DELETE
        return when (answer?.status) {
            SyncStatus.OK -> {
                when {
                    isDelete -> if (current.syncState == SyncState.PENDING_DELETE) dao.deleteById(sent.id)
                    unchanged -> dao.upsert(answer.entry?.toEntity() ?: sent.copy(syncState = SyncState.SYNCED, lastError = null))
                    // Changed while in flight: the server has the entry now, so a later delete must reach it too.
                    current.syncState == SyncState.PENDING_CREATE -> dao.upsert(current.copy(syncState = SyncState.PENDING_UPDATE))
                }
                PushStats(synced = 1)
            }

            SyncStatus.WEEK_CLOSED -> {
                // The week closed before the change reached the server: undo it locally, keeping the server copy
                // (contract §6.5). A newer local change can't apply either, so this also wins over it.
                val server = answer.entry?.toEntity()
                when {
                    server != null -> dao.upsert(server)
                    isDelete -> dao.upsert(current.copy(syncState = SyncState.SYNCED, lastError = null))
                    unchanged -> dao.upsert(current.copy(lastError = answer.message ?: DEFAULT_ERROR))
                }
                if (server != null || isDelete) PushStats(reverted = 1) else PushStats(failed = 1)
            }

            SyncStatus.INVALID, SyncStatus.INVALID_TIMESTAMP, SyncStatus.CONFLICT -> {
                if (unchanged) dao.upsert(current.copy(lastError = answer.message ?: DEFAULT_ERROR))
                PushStats(failed = 1, conflicts = if (answer.status == SyncStatus.CONFLICT) 1 else 0)
            }

            // ERROR, UNKNOWN, or no answer for this item: keep it and retry later.
            else -> PushStats(retryable = 1)
        }
    }

    private fun failureOutcome(error: ApiError): (PushStats) -> PushOutcome = { stats ->
        when (error) {
            is ApiError.Network, is ApiError.Unexpected -> PushOutcome.RetryLater(stats)
            is ApiError.Http -> when {
                error.code == ApiErrorCode.UNAUTHENTICATED -> PushOutcome.Stopped(stats, "signed out")
                // No Menosan account (yet): keep everything and try again later.
                error.code == ApiErrorCode.ACCOUNT_NOT_FOUND -> PushOutcome.RetryLater(stats)
                error.status >= 500 || error.status == 408 || error.status == 429 -> PushOutcome.RetryLater(stats)
                else -> PushOutcome.Stopped(stats, "http ${error.status}")
            }
        }
    }

    private fun SyncStatus.isRetryable() = this == SyncStatus.ERROR || this == SyncStatus.UNKNOWN

    companion object {
        /** Most items one `POST /v1/entries/sync` may carry (contract §6.5). */
        const val MAX_BATCH = 500

        /** Upper bound on outbox passes per run, for changes made while a request was in flight. */
        const val MAX_PASSES = 3

        /** Room keeps the current week plus this many previous weeks (plan §5.7). */
        const val KEPT_PREVIOUS_WEEKS = 2L

        /** Shown when the server gave no reason. Short and blame-free. */
        const val DEFAULT_ERROR = "The server couldn't save this entry."
    }
}

internal fun EntryEntity.toUpsertDto() = SyncUpsertDto(
    id = id,
    name = name,
    subcategory = subcategory,
    quantity = quantity,
    source = source,
    createdAt = createdAt,
)

/** The server copy as a synced row. `category` and `weekStart` come from the server (contract §6.1). */
internal fun EntryDto.toEntity() = EntryEntity(
    id = id,
    name = name,
    category = category,
    subcategory = subcategory,
    quantity = quantity,
    source = source,
    createdAt = createdAt,
    weekStart = weekStart,
    updatedAt = updatedAt,
    syncState = SyncState.SYNCED,
    lastError = null,
)
