package app.menosan.android.feature.photo

import androidx.annotation.StringRes
import app.menosan.android.R
import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.time.WeekCalc
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Why a photo couldn't be turned into a suggestion. Each kind has its own friendly, blame-free message. */
enum class PhotoErrorKind(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    /** True when sending the same photo again may work (network, a Gemini hiccup, a server problem). */
    val canRetrySame: Boolean,
) {
    NETWORK(R.string.photo_error_network_title, R.string.photo_error_network_body, canRetrySame = true),
    NOT_WASTE(R.string.photo_error_not_waste_title, R.string.photo_error_not_waste_body, canRetrySame = false),
    ANALYSIS_FAILED(R.string.photo_error_analysis_title, R.string.photo_error_analysis_body, canRetrySame = true),
    IMAGE_TOO_LARGE(R.string.photo_error_too_large_title, R.string.photo_error_too_large_body, canRetrySame = false),
    RATE_LIMITED(R.string.photo_error_rate_limited_title, R.string.photo_error_rate_limited_body, canRetrySame = false),
    INVALID_IMAGE(R.string.photo_error_invalid_title, R.string.photo_error_invalid_body, canRetrySame = false),
    SIGNED_OUT(R.string.photo_error_signed_out_title, R.string.photo_error_signed_out_body, canRetrySame = true),
    UNREADABLE(R.string.photo_error_unreadable_title, R.string.photo_error_unreadable_body, canRetrySame = false),
    NO_CAMERA(R.string.photo_error_no_camera_title, R.string.photo_error_no_camera_body, canRetrySame = false),
    UNKNOWN(R.string.photo_error_unknown_title, R.string.photo_error_unknown_body, canRetrySame = true),
}

/** [resetsAt] is set for [PhotoErrorKind.RATE_LIMITED] when the server sends `details.resetsAt` (contract §7.1). */
data class PhotoError(val kind: PhotoErrorKind, val resetsAt: Instant? = null) {
    companion object {
        /** Maps a failed `POST /v1/photo-analysis` by `error.code`, not by status (contract §5.1). */
        fun from(error: ApiError): PhotoError = when (error) {
            is ApiError.Network -> PhotoError(PhotoErrorKind.NETWORK)
            is ApiError.Unexpected -> PhotoError(PhotoErrorKind.UNKNOWN)
            is ApiError.Http -> when (error.code) {
                ApiErrorCode.NOT_WASTE -> PhotoError(PhotoErrorKind.NOT_WASTE)
                ApiErrorCode.ANALYSIS_FAILED -> PhotoError(PhotoErrorKind.ANALYSIS_FAILED)
                ApiErrorCode.IMAGE_TOO_LARGE -> PhotoError(PhotoErrorKind.IMAGE_TOO_LARGE)
                ApiErrorCode.RATE_LIMITED -> PhotoError(PhotoErrorKind.RATE_LIMITED, resetsAt = error.resetsAt())
                ApiErrorCode.VALIDATION_FAILED -> PhotoError(PhotoErrorKind.INVALID_IMAGE)
                ApiErrorCode.UNAUTHENTICATED, ApiErrorCode.ACCOUNT_NOT_FOUND -> PhotoError(PhotoErrorKind.SIGNED_OUT)
                else -> PhotoError(PhotoErrorKind.UNKNOWN)
            }
        }

        private fun ApiError.Http.resetsAt(): Instant? {
            val raw = (details["resetsAt"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            return try {
                Instant.parse(raw)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

object PhotoFormats {
    private val resetFormat = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.ENGLISH)

    /** "Thu, Oct 1 at 12:00 am", in Asia/Manila (plan §4), for the daily photo limit. */
    fun resetTime(resetsAt: Instant): String {
        val text = resetFormat.format(resetsAt.atZone(WeekCalc.ZONE))
        return text.replace("AM", "am").replace("PM", "pm")
    }
}
