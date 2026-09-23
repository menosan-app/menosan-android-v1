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

/** Basic report cache queries. AN-3 owns this DAO and extends it. */
@Dao
interface ReportCacheDao {
    @Query("SELECT * FROM reports_cache ORDER BY week_start DESC")
    fun observeAll(): Flow<List<ReportCacheEntity>>

    @Query("SELECT * FROM reports_cache WHERE week_start = :weekStart")
    suspend fun get(weekStart: LocalDate): ReportCacheEntity?

    @Query("SELECT * FROM reports_cache WHERE week_start = :weekStart")
    fun observe(weekStart: LocalDate): Flow<ReportCacheEntity?>

    @Upsert
    suspend fun upsert(report: ReportCacheEntity)

    @Upsert
    suspend fun upsertAll(reports: List<ReportCacheEntity>)

    @Query("DELETE FROM reports_cache WHERE week_start = :weekStart")
    suspend fun delete(weekStart: LocalDate)
}

@Dao
interface TaxonomyDao {
    @Query("SELECT * FROM taxonomy WHERE id = 1")
    suspend fun get(): TaxonomyEntity?

    @Upsert
    suspend fun upsert(taxonomy: TaxonomyEntity)
}
