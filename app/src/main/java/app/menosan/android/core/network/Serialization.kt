package app.menosan.android.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** JSON settings for the API and for report payloads cached in Room. Unknown keys are ignored (additive contract). */
val MenosanJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * ISO-8601 instants (contract §1). Decodes `Z` or an offset. Encodes in UTC with at most millisecond precision,
 * because the server stores milliseconds and rejects an update whose `createdAt` differs from the stored value.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value.truncatedTo(ChronoUnit.MILLIS)))

    override fun deserialize(decoder: Decoder): Instant = OffsetDateTime.parse(decoder.decodeString()).toInstant()
}

/** `YYYY-MM-DD` Manila local dates (contract §1). */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

typealias IsoInstant = @Serializable(with = InstantSerializer::class) Instant
typealias IsoDate = @Serializable(with = LocalDateSerializer::class) LocalDate

/**
 * Encodes an enum by name and decodes an unknown name to [fallback] instead of failing,
 * so an additive server change (a new criterion, status, …) never breaks an older app.
 */
open class FallbackEnumSerializer<E : Enum<E>>(
    serialName: String,
    private val values: Array<E>,
    private val fallback: E,
) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): E {
        val name = decoder.decodeString()
        return values.firstOrNull { it.name == name } ?: fallback
    }
}
