package app.menosan.android.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TaxonomyDao {
    @Query("SELECT * FROM taxonomy WHERE id = 1")
    suspend fun get(): TaxonomyEntity?

    @Upsert
    suspend fun upsert(taxonomy: TaxonomyEntity)
}
