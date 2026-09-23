package app.menosan.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * The latest taxonomy fetched from `GET /v1/taxonomy`, stored as its JSON (a single row, [id] = 1).
 * Kept as a blob so label or example changes never need a Room migration. The bundled asset is the fallback.
 */
@Entity(tableName = "taxonomy")
data class TaxonomyEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val version: Int,
    val json: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
