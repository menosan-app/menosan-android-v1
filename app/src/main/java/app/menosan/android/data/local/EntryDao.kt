package app.menosan.android.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Basic entry queries. AN-1 owns this DAO and extends it for the outbox. */
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
