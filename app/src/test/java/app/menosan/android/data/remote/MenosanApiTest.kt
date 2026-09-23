package app.menosan.android.data.remote

import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.network.ApiResult
import app.menosan.android.core.network.MenosanJson
import app.menosan.android.core.network.safeApiCall
import app.menosan.android.data.remote.dto.CreateAccountRequest
import app.menosan.android.data.remote.dto.EntryPutRequest
import app.menosan.android.data.remote.dto.SyncRequest
import app.menosan.android.data.remote.dto.SyncStatus
import app.menosan.android.data.remote.dto.SyncUpsertDto
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** The Retrofit service against a fake server: paths, bodies, and [safeApiCall] error mapping (contract §1, §5.1). */
class MenosanApiTest {
    private val server = MockWebServer()
    private lateinit var api: MenosanApi

    @Before
    fun setUp() {
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build())
            .addConverterFactory(MenosanJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MenosanApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    private fun json(code: Int, body: String, requestId: String? = null) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .apply { if (requestId != null) addHeader("X-Request-Id", requestId) }
        .body(body)
        .build()

    @Test
    fun `me decodes on success`() = runTest {
        server.enqueue(json(200, """{"id":"u1","email":"a@b.c","displayName":null,"createdAt":"2026-09-23T01:02:03.456Z"}"""))

        val result = safeApiCall { api.me() }

        val me = (result as ApiResult.Success).value
        assertEquals("a@b.c", me.email)
        assertEquals(Instant.parse("2026-09-23T01:02:03.456Z"), me.createdAt)
        assertEquals("/v1/me", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `error envelope maps to an Http error with code, details and request id`() = runTest {
        server.enqueue(json(404, """{"error":{"code":"ACCOUNT_NOT_FOUND","message":"No account.","details":{}}}""", "req-1"))

        val error = (safeApiCall { api.me() } as ApiResult.Failure).error as ApiError.Http

        assertEquals(404, error.status)
        assertEquals(ApiErrorCode.ACCOUNT_NOT_FOUND, error.code)
        assertEquals("No account.", error.message)
        assertEquals("req-1", error.requestId)
    }

    @Test
    fun `validation details expose the field`() = runTest {
        server.enqueue(json(400, """{"error":{"code":"VALIDATION_FAILED","message":"Bad.","details":{"field":"consent"}}}"""))

        val error = (safeApiCall { api.createAccount(CreateAccountRequest(true)) } as ApiResult.Failure).error as ApiError.Http

        assertEquals(ApiErrorCode.VALIDATION_FAILED, error.code)
        assertEquals("consent", error.field)
        assertEquals("""{"consent":true}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun `a non-JSON error body (proxy page) becomes UNKNOWN, not a crash`() = runTest {
        server.enqueue(MockResponse.Builder().code(502).body("<html>Bad gateway</html>").build())

        val error = (safeApiCall { api.me() } as ApiResult.Failure).error as ApiError.Http

        assertEquals(502, error.status)
        assertEquals(ApiErrorCode.UNKNOWN, error.code)
    }

    @Test
    fun `an unknown error code becomes UNKNOWN`() = runTest {
        server.enqueue(json(418, """{"error":{"code":"TEAPOT","message":"x","details":{}}}"""))

        val error = (safeApiCall { api.me() } as ApiResult.Failure).error as ApiError.Http

        assertEquals(ApiErrorCode.UNKNOWN, error.code)
    }

    @Test
    fun `a timeout is a Network error`() = runTest {
        server.enqueue(json(200, "{}").newBuilder().headersDelay(5, TimeUnit.SECONDS).build())

        val result = safeApiCall { api.me() }

        assertTrue((result as ApiResult.Failure).error is ApiError.Network)
    }

    @Test
    fun `204 on a Unit endpoint is a success`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        val result = safeApiCall { api.deleteAccount() }

        assertTrue(result is ApiResult.Success)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `entry put sends the contract body with a millisecond UTC createdAt`() = runTest {
        server.enqueue(json(201, ENTRY_JSON))
        val body = EntryPutRequest(
            name = "Coffee 3-in-1 sachet",
            subcategory = "RES_SACHETS",
            quantity = 3,
            source = EntrySource.MANUAL,
            createdAt = Instant.parse("2026-09-29T01:00:00.123456789Z"),
        )

        val entry = (safeApiCall { api.putEntry("8b0c", body) } as ApiResult.Success).value

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/entries/8b0c", request.url.encodedPath)
        assertEquals(
            """{"name":"Coffee 3-in-1 sachet","subcategory":"RES_SACHETS","quantity":3,"source":"MANUAL","createdAt":"2026-09-29T01:00:00.123Z"}""",
            request.body!!.utf8(),
        )
        assertEquals(LocalDate.of(2026, 9, 27), entry.weekStart)
    }

    @Test
    fun `entries query passes weekStart`() = runTest {
        server.enqueue(json(200, """{"weekStart":"2026-09-20","weekEnd":"2026-09-26","editable":false,"entries":[]}"""))

        val list = (safeApiCall { api.entries("2026-09-20") } as ApiResult.Success).value

        assertEquals("/v1/entries?weekStart=2026-09-20", server.takeRequest().target)
        assertEquals(false, list.editable)
    }

    @Test
    fun `sync decodes per-item results, including an unknown status`() = runTest {
        server.enqueue(
            json(
                200,
                """{"results":[
                  {"id":"a","op":"UPSERT","status":"OK","entry":$ENTRY_JSON,"message":null},
                  {"id":"b","op":"UPSERT","status":"WEEK_CLOSED","entry":$ENTRY_JSON,"message":"That week is closed."},
                  {"id":null,"op":"UPSERT","status":"INVALID","entry":null,"message":"Bad item."},
                  {"id":"c","op":"DELETE","status":"SOMETHING_NEW","entry":null,"message":"?"}
                ]}""",
            ),
        )
        val request = SyncRequest(
            upserts = listOf(SyncUpsertDto("a", "x", "RES_SACHETS", 1, EntrySource.PHOTO, Instant.parse("2026-09-29T01:00:00Z"))),
            deletes = listOf("c"),
        )

        val results = (safeApiCall { api.syncEntries(request) } as ApiResult.Success).value.results

        assertEquals(
            listOf(SyncStatus.OK, SyncStatus.WEEK_CLOSED, SyncStatus.INVALID, SyncStatus.UNKNOWN),
            results.map { it.status },
        )
        assertEquals(null, results[2].id)
        assertEquals("/v1/entries/sync", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `photo analysis sends one multipart part named image`() = runTest {
        server.enqueue(
            json(
                200,
                """{"suggestion":{"name":"Sachet","category":"RESIDUAL","subcategory":"RES_SACHETS","quantity":5,"confidence":0.82},"warning":"Check it."}""",
            ),
        )

        val result = safeApiCall { api.analyzePhoto(MenosanApi.imagePart(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))) }

        assertEquals(5, (result as ApiResult.Success).value.suggestion.quantity)
        val request = server.takeRequest()
        assertTrue(request.headers["Content-Type"]!!.startsWith("multipart/form-data"))
        assertTrue(request.body!!.utf8().contains("""name="image"; filename="photo.jpg""""))
    }

    private companion object {
        const val ENTRY_JSON =
            """{"id":"8b0c","name":"Coffee 3-in-1 sachet","category":"RESIDUAL","subcategory":"RES_SACHETS","quantity":3,"source":"MANUAL","createdAt":"2026-09-29T01:00:00Z","weekStart":"2026-09-27","updatedAt":"2026-09-29T01:00:02Z","editable":true}"""
    }
}
