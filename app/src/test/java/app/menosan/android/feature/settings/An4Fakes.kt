package app.menosan.android.feature.settings

import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.auth.AuthUser
import app.menosan.android.core.model.Entry
import app.menosan.android.core.model.EntryDraft
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.EntrySyncStatus
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.network.ApiResult
import app.menosan.android.core.settings.ThemeMode
import app.menosan.android.data.repo.AdoptionResult
import app.menosan.android.data.repo.EntryRepository
import app.menosan.android.data.repo.RefreshResult
import app.menosan.android.data.repo.ReportListItem
import app.menosan.android.data.repo.ReportRepository
import app.menosan.android.data.repo.ReportView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate

// In-memory fakes for AN-4's view model tests.

class FakeEntries : EntryRepository {
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    val pending = MutableStateFlow(0)
    var syncRequests = 0
    var refreshes = 0
    val deleted = mutableListOf<String>()

    override fun observeWeek(weekStart: LocalDate): Flow<List<Entry>> = entries.map { list -> list.filter { it.weekStart == weekStart } }

    override fun observeCurrentWeek(): Flow<List<Entry>> = entries

    override suspend fun get(id: String): Entry? = entries.value.firstOrNull { it.id == id }

    override suspend fun create(draft: EntryDraft): Entry = error("unused")

    override suspend fun update(id: String, draft: EntryDraft): Entry = error("unused")

    override suspend fun delete(id: String) {
        deleted += id
        entries.value = entries.value.filterNot { it.id == id }
    }

    override fun observePendingCount(): Flow<Int> = pending

    override suspend fun hasPendingChanges(): Boolean = pending.value > 0

    override fun requestSync() {
        syncRequests++
    }

    override suspend fun refreshCurrentWeek() {
        refreshes++
    }
}

class FakeReports : ReportRepository {
    val latest = MutableStateFlow<ReportView?>(null)
    var refreshes = 0

    override fun observeReports(): Flow<List<ReportListItem>> = MutableStateFlow(emptyList())

    override fun observeReport(weekStart: LocalDate): Flow<ReportView?> = latest.map { it?.takeIf { v -> v.weekStart == weekStart } }

    override fun observeLatestReport(): Flow<ReportView?> = latest

    override suspend fun refreshReports(): RefreshResult {
        refreshes++
        return RefreshResult.Success
    }

    override suspend fun refreshReport(weekStart: LocalDate): RefreshResult = RefreshResult.Success

    override suspend fun setAdopted(weekStart: LocalDate, interventionId: String, adopted: Boolean): AdoptionResult =
        AdoptionResult.NotAvailable

    override suspend fun refreshAfterSync(): RefreshResult = RefreshResult.Success

    override suspend fun generateOfflineReports(): List<LocalDate> = emptyList()
}

class FakeNetwork(online: Boolean = true) : NetworkStatus {
    val state = MutableStateFlow(online)
    override val online: Flow<Boolean> get() = state

    override fun isOnline(): Boolean = state.value
}

class FakeAppearance : AppearanceStore {
    private val mode = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeMode: StateFlow<ThemeMode> get() = mode

    override fun setThemeMode(mode: ThemeMode) {
        this.mode.value = mode
    }
}

class FakeAuth(user: AuthUser? = AuthUser("uid-1", "liza@example.com", "Liza Cabaraban")) : AuthService {
    private val state = MutableStateFlow(user)
    var signedOut = false
    override val currentUser: AuthUser? get() = state.value
    override val authState: Flow<AuthUser?> get() = state

    override suspend fun signInWithGoogleIdToken(googleIdToken: String): AuthUser = error("unused")

    override fun signOut() {
        signedOut = true
        state.value = null
    }

    override fun idToken(forceRefresh: Boolean): String? = null
}

/** Records the order of calls, so tests can check "clear local data, then sign out" and retries. */
class FakeAccountActions : AccountActions {
    /** Answers for successive `DELETE /v1/account` calls; the last one repeats. */
    var deleteResults: List<ApiResult<Unit>> = listOf(ApiResult.Success(Unit, 204))
    var exportResult = ExportResult.SUCCESS
    var endSessionFails = false
    val calls = mutableListOf<String>()
    val exportedTo = mutableListOf<String>()
    private var deleteCalls = 0

    override suspend fun deleteAccount(): ApiResult<Unit> {
        calls += "delete"
        return deleteResults[minOf(deleteCalls++, deleteResults.lastIndex)]
    }

    override suspend fun exportTo(documentUri: String): ExportResult {
        calls += "export"
        exportedTo += documentUri
        return exportResult
    }

    override suspend fun endSession() {
        calls += "endSession"
        if (endSessionFails) error("clear failed")
    }
}

fun httpError(status: Int, code: ApiErrorCode = ApiErrorCode.INTERNAL): ApiResult<Unit> =
    ApiResult.Failure(ApiError.Http(status, code, null, JsonObject(emptyMap()), null))

fun testEntry(
    id: String,
    createdAt: Instant,
    weekStart: LocalDate,
    quantity: Int = 1,
    status: EntrySyncStatus = EntrySyncStatus.SYNCED,
    subcategory: String = "RES_SACHETS",
) = Entry(
    id = id,
    name = "Item $id",
    category = WasteCategory.RESIDUAL,
    subcategory = subcategory,
    quantity = quantity,
    source = EntrySource.MANUAL,
    createdAt = createdAt,
    weekStart = weekStart,
    syncStatus = status,
    editable = true,
)
