package app.menosan.android.sync

import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.network.ApiResult
import app.menosan.android.data.local.EntryDao
import app.menosan.android.data.local.EntryEntity
import app.menosan.android.data.local.SyncState
import app.menosan.android.data.remote.dto.EntryDto
import app.menosan.android.data.remote.dto.EntryListDto
import app.menosan.android.data.remote.dto.SyncOp
import app.menosan.android.data.remote.dto.SyncRequest
import app.menosan.android.data.remote.dto.SyncResponse
import app.menosan.android.data.remote.dto.SyncResultDto
import app.menosan.android.data.remote.dto.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** In-memory [EntryDao] with the same filters and ordering as the Room queries. */
class FakeEntryDao : EntryDao {
    val rows = MutableStateFlow<Map<String, EntryEntity>>(emptyMap())

    val all: List<EntryEntity> get() = rows.value.values.toList()

    fun put(vararg entries: EntryEntity) = entries.forEach { e -> rows.value = rows.value + (e.id to e) }

    private fun visible(e: EntryEntity) = e.syncState != SyncState.PENDING_DELETE

    override fun observeWeek(weekStart: LocalDate): Flow<List<EntryEntity>> = rows.map { map ->
        map.values.filter { it.weekStart == weekStart && visible(it) }.sortedByDescending { it.createdAt }
    }

    override suspend fun getWeek(weekStart: LocalDate) = all.filter { it.weekStart == weekStart && visible(it) }

    override suspend fun getById(id: String) = rows.value[id]

    override suspend fun getPending() = all.filter { it.syncState != SyncState.SYNCED }.sortedBy { it.createdAt }

    override fun observePendingCount(): Flow<Int> = rows.map { m -> m.values.count { it.syncState != SyncState.SYNCED } }

    override suspend fun getOutbox() =
        all.filter { it.syncState != SyncState.SYNCED && it.lastError == null }.sortedBy { it.createdAt }

    override suspend fun getWeekIncludingDeleted(weekStart: LocalDate) = all.filter { it.weekStart == weekStart }

    override suspend fun upsert(entry: EntryEntity) = put(entry)

    override suspend fun upsertAll(entries: List<EntryEntity>) = put(*entries.toTypedArray())

    override suspend fun deleteById(id: String) {
        rows.value = rows.value - id
    }

    override suspend fun pruneSyncedBefore(oldestKeptWeekStart: LocalDate): Int {
        val drop = all.filter { it.weekStart < oldestKeptWeekStart && it.syncState == SyncState.SYNCED }
        rows.value = rows.value - drop.map { it.id }.toSet()
        return drop.size
    }
}

/**
 * Fake server. By default it answers every item `OK`, echoing the entry like the backend does. Tests override
 * [statusFor] per item, or [syncFailure] for the whole request, and can run [duringSync] while the request is "in flight".
 */
class FakeEntryRemote(private val serverNow: () -> Instant) : EntryRemote {
    val requests = mutableListOf<SyncRequest>()
    var statusFor: (id: String, op: SyncOp) -> SyncStatus = { _, _ -> SyncStatus.OK }
    var serverCopy: (id: String) -> EntryDto? = { null }
    var syncFailure: ApiError? = null
    var duringSync: suspend () -> Unit = {}
    var currentWeek: ApiResult<EntryListDto> = ApiResult.Failure(ApiError.Network(IOException("offline")))
    var dropResults = false

    override suspend fun sync(request: SyncRequest): ApiResult<SyncResponse> {
        requests += request
        duringSync()
        syncFailure?.let { return ApiResult.Failure(it) }
        val results = request.upserts.map { u ->
            val status = statusFor(u.id, SyncOp.UPSERT)
            val entry = when (status) {
                SyncStatus.OK -> EntryDto(
                    id = u.id, name = u.name.trim(), category = categoryOf(u.subcategory), subcategory = u.subcategory,
                    quantity = u.quantity, source = u.source, createdAt = u.createdAt,
                    weekStart = app.menosan.android.core.time.WeekCalc.weekStart(u.createdAt),
                    updatedAt = serverNow(), editable = true,
                )
                SyncStatus.WEEK_CLOSED -> serverCopy(u.id)
                else -> null
            }
            SyncResultDto(u.id, SyncOp.UPSERT, status, entry, if (status == SyncStatus.OK) null else "Nope ($status).")
        } + request.deletes.map { id ->
            val status = statusFor(id, SyncOp.DELETE)
            SyncResultDto(id, SyncOp.DELETE, status, if (status == SyncStatus.WEEK_CLOSED) serverCopy(id) else null, null)
        }
        return ApiResult.Success(SyncResponse(if (dropResults) emptyList() else results), 200)
    }

    override suspend fun currentWeekEntries(): ApiResult<EntryListDto> = currentWeek

    private fun categoryOf(code: String) = when (code.substringBefore('_')) {
        "BIO" -> WasteCategory.BIODEGRADABLE
        "REC" -> WasteCategory.RECYCLABLE
        "RES" -> WasteCategory.RESIDUAL
        else -> WasteCategory.SPECIAL
    }

    companion object {
        fun http(status: Int, code: ApiErrorCode) = ApiError.Http(status, code, null, JsonObject(emptyMap()), null)
    }
}

object DirectTransactions : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

class FakeSyncNotices : SyncNotices {
    private val count = MutableStateFlow(0)
    override val revertedChanges: StateFlow<Int> = count

    override fun addRevertedChanges(count: Int) {
        this.count.value += count
    }

    override fun clearRevertedChanges() {
        count.value = 0
    }
}

class CountingSyncRequester : SyncRequester {
    var requests = 0
    override fun requestSync() {
        requests++
    }
}

fun entity(
    id: String,
    createdAt: Instant,
    state: SyncState = SyncState.PENDING_CREATE,
    name: String = "Coffee sachet",
    subcategory: String = "RES_SACHETS",
    quantity: Int = 2,
    lastError: String? = null,
) = EntryEntity(
    id = id,
    name = name,
    category = WasteCategory.RESIDUAL,
    subcategory = subcategory,
    quantity = quantity,
    source = EntrySource.MANUAL,
    createdAt = createdAt,
    weekStart = app.menosan.android.core.time.WeekCalc.weekStart(createdAt),
    updatedAt = createdAt,
    syncState = state,
    lastError = lastError,
)

fun EntryEntity.toServerDto(updatedAt: Instant = this.updatedAt, name: String = this.name, quantity: Int = this.quantity) = EntryDto(
    id = id, name = name, category = category, subcategory = subcategory, quantity = quantity, source = source,
    createdAt = createdAt, weekStart = weekStart, updatedAt = updatedAt, editable = true,
)
