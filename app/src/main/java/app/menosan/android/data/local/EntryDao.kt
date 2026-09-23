package app.menosan.android.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Entry queries. AN-1 owns this DAO. AN-3 reads [getWeek] and [getPending], so don't rename or remove methods. */
@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE week_start = :weekStart AND sync_state != 'PENDING_DELETE' ORDER BY created_at DESC")
    fun observeWeek(weekStart: LocalDate): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE week_start = :weekStart AND sync_state != 'PENDING_DELETE'")
    suspend fun getWeek(weekStart: LocalDate): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE sync_state != 'SYNCED' ORDER BY created_at")
    suspend fun getPending(): List<EntryEntity>

    @Query("SELECT COUNT(*) FROM entries WHERE sync_state != 'SYNCED'")
    fun observePendingCount(): Flow<Int>

    /**
     * What the next sync sends: every local change that hasn't been answered with a final error. Rows with
     * [EntryEntity.lastError] ("Couldn't sync") wait until the user edits or deletes them, so they aren't retried forever.
     */
    @Query("SELECT * FROM entries WHERE sync_state != 'SYNCED' AND last_error IS NULL ORDER BY created_at")
    suspend fun getOutbox(): List<EntryEntity>

    /** Every row of one week, including pending deletes (used by the merge after `GET /v1/entries`). */
    @Query("SELECT * FROM entries WHERE week_start = :weekStart")
    suspend fun getWeekIncludingDeleted(weekStart: LocalDate): List<EntryEntity>

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Retention (plan §5.7): drops synced entries older than [oldestKeptWeekStart]. Pending ones are never pruned. */
    @Query("DELETE FROM entries WHERE week_start < :oldestKeptWeekStart AND sync_state = 'SYNCED'")
    suspend fun pruneSyncedBefore(oldestKeptWeekStart: LocalDate): Int
}
