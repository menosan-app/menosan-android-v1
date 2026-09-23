package app.menosan.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The on-device database. Schemas are exported to `app/schemas/` and must be committed.
 *
 * ⚠️ From the first tester build (Sat 9/26) on, NEVER use `fallbackToDestructiveMigration`. Every schema change
 * bumps [version] and adds a real `Migration` to [MIGRATIONS] (plan §10, NFR7).
 */
@Database(
    entities = [EntryEntity::class, ReportCacheEntity::class, TaxonomyEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MenosanDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun reportCacheDao(): ReportCacheDao
    abstract fun taxonomyDao(): TaxonomyDao

    companion object {
        const val NAME = "menosan.db"

        /** Add each `Migration(n, n + 1)` here. */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()
    }
}
