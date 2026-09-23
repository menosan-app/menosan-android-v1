package app.menosan.android.core.network

import app.menosan.android.data.remote.dto.ErrorEnvelope
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import java.io.IOException

/** Error codes of contract §1. Switch on these, not on the HTTP status (contract §5.1). */
enum class ApiErrorCode {
    UNAUTHENTICATED, ACCOUNT_NOT_FOUND, VALIDATION_FAILED, INVALID_TIMESTAMP, WEEK_CLOSED, NOT_FOUND, CONFLICT,
    ADOPTION_WINDOW_CLOSED, ANALYSIS_FAILED, NOT_WASTE, IMAGE_TOO_LARGE, RATE_LIMITED, INTERNAL,

    /** A code this app doesn't know, or a non-JSON error body (e.g. a proxy page while the server wakes up). */
    UNKNOWN;

    companion object {
        fun from(code: String?): ApiErrorCode = entries.firstOrNull { it.name == code } ?: UNKNOWN
    }
}

sealed interface ApiError {
    /** The server answered with a non-2xx status. [message] is short, user-safe English from the server. */
    data class Http(
        val status: Int,
        val code: ApiErrorCode,
        val message: String?,
        val details: JsonObject,
        val requestId: String?,
    ) : ApiError {
        /** `details.field` for `VALIDATION_FAILED`, when the server names one. */
        val field: String? get() = (details["field"] as? JsonPrimitive)?.content
    }

    /** No connection, DNS failure, timeout, or the connection dropped. Safe to retry later. */
    data class Network(val cause: IOException) : ApiError

    /** A bug or an unexpected response shape (e.g. JSON that doesn't match the DTO). */
    data class Unexpected(val cause: Throwable) : ApiError
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T, val status: Int) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value), status)
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

/** True when the failure has this error code. */
fun ApiResult<*>.isError(code: ApiErrorCode): Boolean =
    ((this as? ApiResult.Failure)?.error as? ApiError.Http)?.code == code

/**
 * Runs one Retrofit call and never throws (except coroutine cancellation). A 2xx response becomes [ApiResult.Success];
 * a `204` or empty body is allowed only when [T] is [Unit].
 */
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> {
    val response = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        return ApiResult.Failure(ApiError.Network(e))
    } catch (e: Exception) {
        return ApiResult.Failure(ApiError.Unexpected(e))
    }
    if (response.isSuccessful) {
        val body = response.body()
        @Suppress("UNCHECKED_CAST")
        return when {
            body != null -> ApiResult.Success(body, response.code())
            // Retrofit returns Unit for an empty body only when the declared type is Unit.
            else -> ApiResult.Success(Unit as T, response.code())
        }
    }
    return ApiResult.Failure(response.toHttpError())
}

fun Response<*>.toHttpError(): ApiError.Http {
    val raw = try {
        errorBody()?.string()
    } catch (_: IOException) {
        null
    }
    val envelope = raw?.let { runCatching { MenosanJson.decodeFromString(ErrorEnvelope.serializer(), it) }.getOrNull() }
    return ApiError.Http(
        status = code(),
        code = ApiErrorCode.from(envelope?.error?.code),
        message = envelope?.error?.message,
        details = envelope?.error?.details ?: JsonObject(emptyMap()),
        requestId = headers()[REQUEST_ID_HEADER],
    )
}

const val REQUEST_ID_HEADER = "X-Request-Id"
