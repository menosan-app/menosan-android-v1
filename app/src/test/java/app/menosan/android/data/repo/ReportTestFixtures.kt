package app.menosan.android.data.repo

import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.Taxonomy
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.network.MenosanJson
import app.menosan.android.data.local.EntryEntity
import app.menosan.android.data.local.ReportCacheDao
import app.menosan.android.data.local.ReportCacheEntity
import app.menosan.android.data.local.SyncState
import app.menosan.android.data.remote.MenosanApi
import app.menosan.android.data.remote.dto.AdoptRequest
import app.menosan.android.data.remote.dto.CategoryStatsDto
import app.menosan.android.data.remote.dto.CostLevel
import app.menosan.android.data.remote.dto.CreateAccountRequest
import app.menosan.android.data.remote.dto.CurrentWeekDto
import app.menosan.android.data.remote.dto.Effort
import app.menosan.android.data.remote.dto.EntryDto
import app.menosan.android.data.remote.dto.EntryListDto
import app.menosan.android.data.remote.dto.EntryPutRequest
import app.menosan.android.data.remote.dto.ErrorBody
import app.menosan.android.data.remote.dto.ErrorEnvelope
import app.menosan.android.data.remote.dto.HealthDto
import app.menosan.android.data.remote.dto.HotspotCriterion
import app.menosan.android.data.remote.dto.HotspotDto
import app.menosan.android.data.remote.dto.InterventionType
import app.menosan.android.data.remote.dto.MeDto
import app.menosan.android.data.remote.dto.PhotoAnalysisDto
import app.menosan.android.data.remote.dto.RecommendationDto
import app.menosan.android.data.remote.dto.ReportDto
import app.menosan.android.data.remote.dto.ReportSummaryDto
import app.menosan.android.data.remote.dto.SubcategoryStatsDto
import app.menosan.android.data.remote.dto.SyncRequest
import app.menosan.android.data.remote.dto.SyncResponse
import app.menosan.android.data.remote.dto.TotalsDto
import app.menosan.android.data.remote.dto.WeeklyStatsDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Shared test data: Tue 2026-10-06 10:00 PHT, so the current week starts Sun 10-04 and the latest report is 09-27. */
object ReportFixtures {
    val NOW: Instant = Instant.parse("2026-10-06T02:00:00Z")
    val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    val CURRENT_WEEK: LocalDate = LocalDate.of(2026, 10, 4)
    val LATEST_WEEK: LocalDate = LocalDate.of(2026, 9, 27) // W: the latest report week.
    val PREVIOUS_WEEK: LocalDate = LocalDate.of(2026, 9, 20) // W−1.

    val TAXONOMY: Taxonomy = TaxonomyRepository.parseTaxonomy(File("src/main/assets/taxonomy.json").readText())

    fun entry(
        subcategory: String,
        quantity: Int,
        weekStart: LocalDate,
        syncState: SyncState = SyncState.SYNCED,
    ): EntryEntity {
        val created = weekStart.atStartOfDay(ZoneOffset.ofHours(8)).plusHours(10).toInstant()
        return EntryEntity(
            id = UUID.randomUUID().toString(),
            name = "item",
            category = TAXONOMY.subcategory(subcategory)!!.category,
            subcategory = subcategory,
            quantity = quantity,
            source = EntrySource.MANUAL,
            createdAt = created,
            weekStart = weekStart,
            updatedAt = created,
            syncState = syncState,
        )
    }

    fun recommendation(id: String, adopted: Boolean = false, title: String = "Tip $id") = RecommendationDto(
        interventionId = id,
        code = "CODE_$id",
        type = InterventionType.PREVENT,
        title = title,
        description = "Try this week.",
        howTo = listOf("Step one", "Step two"),
        costLevel = CostLevel.FREE,
        effort = Effort.LOW,
        note = null,
        continued = false,
        adopted = adopted,
    )

    /** A server report with one hotspot per (subcategory, quantity), each with the given recommendations. */
    fun serverReport(
        weekStart: LocalDate,
        hotspots: List<Pair<String, Int>>,
        recommendations: Map<String, List<RecommendationDto>> = emptyMap(),
        isLatest: Boolean = weekStart == LATEST_WEEK,
        revision: Int = 1,
    ): ReportDto {
        val subs = hotspots.map { (code, q) ->
            SubcategoryStatsDto(code, TAXONOMY.subcategory(code)!!.category, frequency = 1, quantity = q)
        }
        val total = subs.sumOf { it.quantity }
        return ReportDto(
            weekStart = weekStart,
            weekEnd = weekStart.plusDays(6),
            revision = revision,
            isLatest = isLatest,
            stats = WeeklyStatsDto(
                analyzedTotals = TotalsDto(subs.size, total),
                categories = listOf(WasteCategory.BIODEGRADABLE, WasteCategory.RECYCLABLE, WasteCategory.RESIDUAL).map { cat ->
                    val q = subs.filter { it.category == cat }.sumOf { it.quantity }
                    CategoryStatsDto(cat, subs.count { it.category == cat }, q, if (total == 0) 0.0 else q * 100.0 / total)
                },
                subcategories = subs,
                special = TotalsDto(0, 0),
            ),
            hotspots = hotspots.mapIndexed { i, (code, q) ->
                HotspotDto(i + 1, code, listOf(HotspotCriterion.AVOIDABLE), 1, q, 1.0, recommendations[code].orEmpty())
            },
        )
    }

    fun summary(report: ReportDto) = ReportSummaryDto(
        weekStart = report.weekStart,
        weekEnd = report.weekEnd,
        analyzedQuantity = report.stats.analyzedTotals.quantity,
        hotspotCount = report.hotspots.size,
        adoptedCount = report.adoptedCount(),
        isLatest = report.isLatest,
    )
}

/** In-memory `reports_cache`. */
class FakeReportCacheDao : ReportCacheDao {
    val rows = MutableStateFlow<Map<LocalDate, ReportCacheEntity>>(emptyMap())

    override fun observeAll(): Flow<List<ReportCacheEntity>> =
        rows.map { map -> map.values.sortedByDescending { it.weekStart } }

    override suspend fun get(weekStart: LocalDate): ReportCacheEntity? = rows.value[weekStart]

    override suspend fun getAll(): List<ReportCacheEntity> = rows.value.values.sortedByDescending { it.weekStart }

    override fun observe(weekStart: LocalDate): Flow<ReportCacheEntity?> = rows.map { it[weekStart] }

    override suspend fun upsert(report: ReportCacheEntity) {
        rows.value = rows.value + (report.weekStart to report)
    }

    override suspend fun upsertAll(reports: List<ReportCacheEntity>) {
        rows.value = rows.value + reports.associateBy { it.weekStart }
    }

    override suspend fun delete(weekStart: LocalDate) {
        rows.value = rows.value - weekStart
    }
}

class FakeEntrySource(var entries: List<EntryEntity> = emptyList()) : ReportEntrySource {
    override suspend fun entriesOf(weekStart: LocalDate): List<EntryEntity> =
        entries.filter { it.weekStart == weekStart && it.syncState != SyncState.PENDING_DELETE }

    override suspend fun weeksWithPendingEntries(): Set<LocalDate> =
        entries.filter { it.syncState != SyncState.SYNCED }.map { it.weekStart }.toSet()
}

/**
 * The report endpoints of [MenosanApi], answered from in-memory server state. Set [offline] to make every call fail
 * like a dropped connection. Other endpoints aren't used by reports.
 */
class FakeReportsApi : MenosanApi {
    var offline = false
    val reports = linkedMapOf<LocalDate, ReportDto>()
    var adoptionError: Pair<Int, String>? = null
    val calls = mutableListOf<String>()

    /** Suspends adoption calls until completed, to observe the optimistic state. */
    var adoptionGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    private fun <T> ok(value: T): Response<T> = Response.success(value)

    private fun <T> error(status: Int, code: String): Response<T> = Response.error(
        status,
        MenosanJson.encodeToString(ErrorEnvelope.serializer(), ErrorEnvelope(ErrorBody(code, "msg")))
            .toResponseBody("application/json".toMediaType()),
    )

    private fun checkOnline() {
        if (offline) throw IOException("offline")
    }

    override suspend fun reports(): Response<List<ReportSummaryDto>> {
        calls += "GET /v1/reports"
        checkOnline()
        return ok(reports.values.sortedByDescending { it.weekStart }.map { ReportFixtures.summary(it) })
    }

    override suspend fun report(weekStart: String): Response<ReportDto> {
        calls += "GET /v1/reports/$weekStart"
        checkOnline()
        return reports[LocalDate.parse(weekStart)]?.let { ok(it) } ?: error(404, "NOT_FOUND")
    }

    override suspend fun adopt(weekStart: String, body: AdoptRequest): Response<ReportDto> =
        changeAdoption("POST", weekStart, body.interventionIds.single(), adopted = true)

    override suspend fun unadopt(weekStart: String, interventionId: String): Response<ReportDto> =
        changeAdoption("DELETE", weekStart, interventionId, adopted = false)

    private suspend fun changeAdoption(method: String, weekStart: String, id: String, adopted: Boolean): Response<ReportDto> {
        calls += "$method adoption $weekStart $id"
        adoptionGate?.await()
        checkOnline()
        adoptionError?.let { (status, code) -> return error(status, code) }
        val week = LocalDate.parse(weekStart)
        val report = reports[week] ?: return error(404, "NOT_FOUND")
        val updated = report.copy(
            hotspots = report.hotspots.map { h ->
                h.copy(recommendations = h.recommendations.map { if (it.interventionId == id) it.copy(adopted = adopted) else it })
            },
        )
        reports[week] = updated
        return ok(updated)
    }

    override suspend fun health(): Response<HealthDto> = unused()
    override suspend fun taxonomy(): Response<Taxonomy> = unused()
    override suspend fun me(): Response<MeDto> = unused()
    override suspend fun createAccount(body: CreateAccountRequest): Response<MeDto> = unused()
    override suspend fun deleteAccount(): Response<Unit> = unused()
    override suspend fun export(): Response<ResponseBody> = unused()
    override suspend fun currentWeek(): Response<CurrentWeekDto> = unused()
    override suspend fun entries(weekStart: String?): Response<EntryListDto> = unused()
    override suspend fun putEntry(id: String, body: EntryPutRequest): Response<EntryDto> = unused()
    override suspend fun deleteEntry(id: String): Response<Unit> = unused()
    override suspend fun syncEntries(body: SyncRequest): Response<SyncResponse> = unused()
    override suspend fun analyzePhoto(image: MultipartBody.Part): Response<PhotoAnalysisDto> = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Not used by reports")
}
