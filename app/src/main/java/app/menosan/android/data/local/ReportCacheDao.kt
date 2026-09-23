package app.menosan.android.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Basic report cache queries. AN-3 owns this DAO and extends it. */
@Dao
interface ReportCacheDao {
    @Query("SELECT * FROM reports_cache ORDER BY week_start DESC")
    fun observeAll(): Flow<List<ReportCacheEntity>>

    @Query("SELECT * FROM reports_cache WHERE week_start = :weekStart")
    suspend fun get(weekStart: LocalDate): ReportCacheEntity?

    /** Every cached row, newest week first (AN-3: list sync and offline reports). */
    @Query("SELECT * FROM reports_cache ORDER BY week_start DESC")
    suspend fun getAll(): List<ReportCacheEntity>

    @Query("SELECT * FROM reports_cache WHERE week_start = :weekStart")
    fun observe(weekStart: LocalDate): Flow<ReportCacheEntity?>

    @Upsert
    suspend fun upsert(report: ReportCacheEntity)

    @Upsert
    suspend fun upsertAll(reports: List<ReportCacheEntity>)

    @Query("DELETE FROM reports_cache WHERE week_start = :weekStart")
    suspend fun delete(weekStart: LocalDate)
}
