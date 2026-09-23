package app.menosan.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.WasteCategory
import java.time.Instant
import java.time.LocalDate

/** Outbox state of a local entry (plan AN-1). */
enum class SyncState { PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE, SYNCED }

/**
 * A waste entry. Room is the source of truth for the current week and keeps the two previous weeks too
 * (plan §5.7). [id] is a client-generated UUID and [createdAt] is set at save time (millisecond precision).
 * [weekStart] is derived locally with `WeekCalc`; the server derives its own from [createdAt].
 * Owned by AN-1 from here on. Any schema change after the first tester build needs a real Migration.
 */
@Entity(
    tableName = "entries",
    indices = [Index("week_start"), Index("sync_state")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: WasteCategory,
    val subcategory: String,
    val quantity: Int,
    val source: EntrySource,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "week_start") val weekStart: LocalDate,
    /** Last local change, or the server's `updatedAt` after a sync. */
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "sync_state") val syncState: SyncState,
    /** Short, user-safe reason from the last failed sync attempt, if any. */
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)
