package app.menosan.android.data.repo

import android.content.Context
import app.menosan.android.core.model.Taxonomy
import app.menosan.android.core.network.ApiResult
import app.menosan.android.core.network.MenosanJson
import app.menosan.android.core.network.safeApiCall
import app.menosan.android.data.local.TaxonomyDao
import app.menosan.android.data.local.TaxonomyEntity
import app.menosan.android.data.remote.MenosanApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The waste taxonomy (plan §3). Works offline: it uses the copy from `GET /v1/taxonomy` stored in Room when that is at
 * least as new as the bundled `assets/taxonomy.json`, and the bundled copy otherwise.
 */
@Singleton
class TaxonomyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TaxonomyDao,
    private val api: MenosanApi,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: Taxonomy? = null

    suspend fun taxonomy(): Taxonomy = cached ?: mutex.withLock {
        cached ?: load().also { cached = it }
    }

    /** Fetches the server copy and stores it if it isn't older. Returns false when offline or on error. */
    suspend fun refresh(): Boolean {
        val remote = when (val result = safeApiCall { api.taxonomy() }) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return false
        }
        mutex.withLock {
            if (remote.version < bundled().version) return false
            dao.upsert(
                TaxonomyEntity(
                    version = remote.version,
                    json = MenosanJson.encodeToString(Taxonomy.serializer(), remote),
                    fetchedAt = clock.instant(),
                ),
            )
            cached = remote
        }
        return true
    }

    private suspend fun load(): Taxonomy {
        val bundled = bundled()
        val stored = dao.get()
            ?.takeIf { it.version >= bundled.version }
            ?.let { runCatching { MenosanJson.decodeFromString(Taxonomy.serializer(), it.json) }.getOrNull() }
        return stored ?: bundled
    }

    private var bundledCache: Taxonomy? = null

    private suspend fun bundled(): Taxonomy = bundledCache ?: withContext(Dispatchers.IO) {
        context.assets.open(ASSET).bufferedReader().use { parseTaxonomy(it.readText()) }
    }.also { bundledCache = it }

    companion object {
        const val ASSET = "taxonomy.json"

        fun parseTaxonomy(json: String): Taxonomy = MenosanJson.decodeFromString(Taxonomy.serializer(), json)
    }
}
