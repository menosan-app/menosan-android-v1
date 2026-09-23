package app.menosan.android.feature.photo

import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class PhotoErrorTest {
    private fun http(status: Int, code: ApiErrorCode, details: Map<String, JsonPrimitive> = emptyMap()) =
        ApiError.Http(status, code, "Server message", JsonObject(details), "req-1")

    @Test
    fun `every photo error code has its own kind`() {
        val cases = mapOf(
            http(422, ApiErrorCode.NOT_WASTE) to PhotoErrorKind.NOT_WASTE,
            http(422, ApiErrorCode.ANALYSIS_FAILED) to PhotoErrorKind.ANALYSIS_FAILED,
            http(413, ApiErrorCode.IMAGE_TOO_LARGE) to PhotoErrorKind.IMAGE_TOO_LARGE,
            http(429, ApiErrorCode.RATE_LIMITED) to PhotoErrorKind.RATE_LIMITED,
            http(400, ApiErrorCode.VALIDATION_FAILED) to PhotoErrorKind.INVALID_IMAGE,
            http(415, ApiErrorCode.VALIDATION_FAILED) to PhotoErrorKind.INVALID_IMAGE,
            http(401, ApiErrorCode.UNAUTHENTICATED) to PhotoErrorKind.SIGNED_OUT,
            http(404, ApiErrorCode.ACCOUNT_NOT_FOUND) to PhotoErrorKind.SIGNED_OUT,
            http(500, ApiErrorCode.INTERNAL) to PhotoErrorKind.UNKNOWN,
            http(502, ApiErrorCode.UNKNOWN) to PhotoErrorKind.UNKNOWN,
            ApiError.Network(IOException("offline")) to PhotoErrorKind.NETWORK,
            ApiError.Unexpected(IllegalStateException("bad json")) to PhotoErrorKind.UNKNOWN,
        )
        cases.forEach { (error, kind) -> assertEquals("$error", kind, PhotoError.from(error).kind) }
    }

    @Test
    fun `the code decides, not the status`() {
        // A 422 is NOT_WASTE or ANALYSIS_FAILED depending on the code (contract §5.1).
        assertEquals(PhotoErrorKind.NOT_WASTE, PhotoError.from(http(422, ApiErrorCode.NOT_WASTE)).kind)
        assertEquals(PhotoErrorKind.ANALYSIS_FAILED, PhotoError.from(http(422, ApiErrorCode.ANALYSIS_FAILED)).kind)
    }

    @Test
    fun `only errors that a resend can fix offer Try again`() {
        val retryable = PhotoErrorKind.entries.filter { it.canRetrySame }.toSet()
        assertEquals(
            setOf(PhotoErrorKind.NETWORK, PhotoErrorKind.ANALYSIS_FAILED, PhotoErrorKind.SIGNED_OUT, PhotoErrorKind.UNKNOWN),
            retryable,
        )
    }

    @Test
    fun `rate limit carries resetsAt`() {
        val error = PhotoError.from(
            http(429, ApiErrorCode.RATE_LIMITED, mapOf("limit" to JsonPrimitive(30), "resetsAt" to JsonPrimitive("2026-09-30T16:00:00Z"))),
        )
        assertEquals(PhotoErrorKind.RATE_LIMITED, error.kind)
        assertEquals(Instant.parse("2026-09-30T16:00:00Z"), error.resetsAt)
    }

    @Test
    fun `a missing or broken resetsAt is ignored`() {
        assertNull(PhotoError.from(http(429, ApiErrorCode.RATE_LIMITED)).resetsAt)
        assertNull(PhotoError.from(http(429, ApiErrorCode.RATE_LIMITED, mapOf("resetsAt" to JsonPrimitive("soon")))).resetsAt)
        assertNull(PhotoError.from(http(429, ApiErrorCode.RATE_LIMITED, mapOf("resetsAt" to JsonPrimitive(123)))).resetsAt)
    }

    @Test
    fun `reset time is shown in Manila time`() {
        // 16:00 UTC is midnight in Manila; Oct 1, 2026 is a Thursday.
        assertEquals("Thu, Oct 1 at 12:00 am", PhotoFormats.resetTime(Instant.parse("2026-09-30T16:00:00Z")))
        assertEquals("Wed, Sep 30 at 1:30 pm", PhotoFormats.resetTime(Instant.parse("2026-09-30T05:30:00Z")))
    }

    @Test
    fun `every kind has a title and a body`() {
        PhotoErrorKind.entries.forEach {
            assertTrue(it.title != 0)
            assertTrue(it.body != 0)
            assertFalse(it.title == it.body)
        }
    }
}
