package app.menosan.android.data.repo

import app.menosan.android.core.model.Entry
import app.menosan.android.core.model.EntryDraft
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Why a local change was refused before it reached the outbox. */
sealed class EntryChangeException(message: String) : Exception(message) {
    /** The entry's week is over, so it can't be edited or deleted (SFR11). */
    class WeekClosed : EntryChangeException("That week is closed.")
    class NotFound : EntryChangeException("Entry not found.")
    class Invalid(val field: String) : EntryChangeException("Invalid $field.")
}

/**
 * Waste entries (plan AN-1). **Room is the source of truth**: every change is written to Room first and then synced
 * by `SyncWorker` through `POST /v1/entries/sync`, so all of this works offline (UFR5–6, NFR7).
 *
 * The signatures are shared with AN-2 (photo save) and AN-4 (logout, dashboard). Change them only by agreement,
 * recorded in docs/HANDOFF.md. AN-1 owns the implementation.
 */
interface EntryRepository {
    /** Entries of one Manila week, newest first. Pending deletes are hidden. */
    fun observeWeek(weekStart: LocalDate): Flow<List<Entry>>

    /** Entries of the current week, newest first. Follows the week rollover. */
    fun observeCurrentWeek(): Flow<List<Entry>>

    suspend fun get(id: String): Entry?

    /**
     * Saves a new entry with a client UUID and `createdAt = now`, then requests a sync.
     * @throws EntryChangeException.Invalid when the draft breaks a field rule.
     */
    suspend fun create(draft: EntryDraft): Entry

    /**
     * Edits name, subcategory, and quantity of a current-week entry (source and `createdAt` never change).
     * @throws EntryChangeException.WeekClosed, [EntryChangeException.NotFound], [EntryChangeException.Invalid].
     */
    suspend fun update(id: String, draft: EntryDraft): Entry

    /** Deletes a current-week entry. @throws EntryChangeException.WeekClosed, [EntryChangeException.NotFound]. */
    suspend fun delete(id: String)

    /** Number of entries not yet confirmed by the server (the pending-sync badge). */
    fun observePendingCount(): Flow<Int>

    /** True when the outbox isn't empty. Logout must warn first (plan §10). */
    suspend fun hasPendingChanges(): Boolean

    /** Enqueues the unique sync work (runs when online, with backoff). */
    fun requestSync()

    /** Pulls `GET /v1/entries` for the current week and merges it (server wins for synced rows). */
    suspend fun refreshCurrentWeek()
}
