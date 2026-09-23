package app.menosan.android.data.remote.dto

import app.menosan.android.core.network.IsoDate
import app.menosan.android.core.network.IsoInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** `GET /v1/me`, and the `200`/`201` body of `POST /v1/account` (contract §5.4–5.5). */
@Serializable
data class MeDto(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val createdAt: IsoInstant,
)

/** `POST /v1/account`. `consent` must be `true` (contract §5.4). */
@Serializable
data class CreateAccountRequest(val consent: Boolean)

/** `GET /v1/weeks/current` (contract §5.5). */
@Serializable
data class CurrentWeekDto(
    val weekStart: IsoDate,
    val weekEnd: IsoDate,
    val timezone: String,
    val serverNow: IsoInstant,
)

/** `GET /health` (contract §5.2). */
@Serializable
data class HealthDto(val status: String, val db: String)

/** Every error response: `{"error": {"code", "message", "details"}}` (contract §1, §5.1). */
@Serializable
data class ErrorEnvelope(val error: ErrorBody)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String? = null,
    val details: JsonObject = JsonObject(emptyMap()),
)
