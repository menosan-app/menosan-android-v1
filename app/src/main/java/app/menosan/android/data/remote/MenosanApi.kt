package app.menosan.android.data.remote

import app.menosan.android.core.model.Taxonomy
import app.menosan.android.data.remote.dto.AdoptRequest
import app.menosan.android.data.remote.dto.CreateAccountRequest
import app.menosan.android.data.remote.dto.CurrentWeekDto
import app.menosan.android.data.remote.dto.EntryDto
import app.menosan.android.data.remote.dto.EntryListDto
import app.menosan.android.data.remote.dto.EntryPutRequest
import app.menosan.android.data.remote.dto.HealthDto
import app.menosan.android.data.remote.dto.MeDto
import app.menosan.android.data.remote.dto.PhotoAnalysisDto
import app.menosan.android.data.remote.dto.ReportDto
import app.menosan.android.data.remote.dto.ReportSummaryDto
import app.menosan.android.data.remote.dto.SyncRequest
import app.menosan.android.data.remote.dto.SyncResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Every app-facing endpoint of API contract v1 (`docs/api-contract.md` §2). Paths are relative to
 * `BuildConfig.API_BASE_URL`. Dates are `YYYY-MM-DD` strings (use `LocalDate.toString()`).
 *
 * Every call returns [Response] so callers can go through [safeApiCall], which turns error bodies into
 * [ApiError] and never throws. `AuthInterceptor` adds the Firebase ID token.
 */
interface MenosanApi {
    @GET("health")
    suspend fun health(): Response<HealthDto>

    @GET("v1/taxonomy")
    suspend fun taxonomy(): Response<Taxonomy>

    // Account (SFR1–4)

    /** `404 ACCOUNT_NOT_FOUND` when the Google account has no Menosan account yet. */
    @GET("v1/me")
    suspend fun me(): Response<MeDto>

    /** Idempotent: `201` when created, `200` when it already exists. */
    @POST("v1/account")
    suspend fun createAccount(@Body body: CreateAccountRequest): Response<MeDto>

    /** `204`, also when already gone. Deletes the Firebase user too. */
    @DELETE("v1/account")
    suspend fun deleteAccount(): Response<Unit>

    /** Streams the JSON export; write it to the SAF document as-is (contract §6.6). */
    @Streaming
    @GET("v1/export")
    suspend fun export(): Response<ResponseBody>

    @GET("v1/weeks/current")
    suspend fun currentWeek(): Response<CurrentWeekDto>

    // Entries (SFR5–6, SFR10–11)

    /** Omit [weekStart] for the current week. */
    @GET("v1/entries")
    suspend fun entries(@Query("weekStart") weekStart: String? = null): Response<EntryListDto>

    @PUT("v1/entries/{id}")
    suspend fun putEntry(@Path("id") id: String, @Body body: EntryPutRequest): Response<EntryDto>

    @DELETE("v1/entries/{id}")
    suspend fun deleteEntry(@Path("id") id: String): Response<Unit>

    /** The offline outbox. Items succeed or fail on their own (contract §6.5). */
    @POST("v1/entries/sync")
    suspend fun syncEntries(@Body body: SyncRequest): Response<SyncResponse>

    // Photo (SFR7–9)

    /** One JPEG part named `image`, at most 2 MB (contract §7.1). */
    @Multipart
    @POST("v1/photo-analysis")
    suspend fun analyzePhoto(@Part image: MultipartBody.Part): Response<PhotoAnalysisDto>

    // Reports and adoption (SFR12–18)

    @GET("v1/reports")
    suspend fun reports(): Response<List<ReportSummaryDto>>

    @GET("v1/reports/{weekStart}")
    suspend fun report(@Path("weekStart") weekStart: String): Response<ReportDto>

    @POST("v1/reports/{weekStart}/adoptions")
    suspend fun adopt(@Path("weekStart") weekStart: String, @Body body: AdoptRequest): Response<ReportDto>

    @DELETE("v1/reports/{weekStart}/adoptions/{interventionId}")
    suspend fun unadopt(
        @Path("weekStart") weekStart: String,
        @Path("interventionId") interventionId: String,
    ): Response<ReportDto>

    companion object {
        private val JPEG = "image/jpeg".toMediaType()

        /** Builds the `image` part for [analyzePhoto]. */
        fun imagePart(jpeg: ByteArray): MultipartBody.Part =
            MultipartBody.Part.createFormData("image", "photo.jpg", jpeg.toRequestBody(JPEG))
    }
}
