package app.menosan.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Cached reports for offline viewing (plan §5.7, AN-3). One row per week.
 *
 * - A row from `GET /v1/reports` has the summary columns and a null [payloadJson].
 * - A full server report (`GET /v1/reports/{weekStart}`) also has [payloadJson]: the `ReportDto` JSON as received,
 *   including recommendations and adoption flags.
 * - An offline provisional report (`LocalReportGenerator`) has [isProvisional] = true. The server report replaces it.
 */
@Entity(tableName = "reports_cache")
data class ReportCacheEntity(
    @PrimaryKey @ColumnInfo(name = "week_start") val weekStart: LocalDate,
    @ColumnInfo(name = "week_end") val weekEnd: LocalDate,
    @ColumnInfo(name = "analyzed_quantity") val analyzedQuantity: Int,
    @ColumnInfo(name = "hotspot_count") val hotspotCount: Int,
    @ColumnInfo(name = "adopted_count") val adoptedCount: Int,
    @ColumnInfo(name = "is_latest") val isLatest: Boolean,
    @ColumnInfo(name = "is_provisional") val isProvisional: Boolean,
    /** Server `revision`, or null for a provisional report. */
    val revision: Int?,
    /** `ALGORITHM_VERSION` used for a provisional report, or null for a server report. */
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: Int?,
    @ColumnInfo(name = "payload_json") val payloadJson: String?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
)
