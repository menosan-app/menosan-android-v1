package app.menosan.android.data.remote.dto

import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.network.FallbackEnumSerializer
import app.menosan.android.core.network.IsoDate
import app.menosan.android.core.network.IsoInstant
import kotlinx.serialization.Serializable

/** An entry as the server returns it (contract §6.1). `category` and `weekStart` are derived by the server. */
@Serializable
data class EntryDto(
    val id: String,
    val name: String,
    val category: WasteCategory,
    val subcategory: String,
    val quantity: Int,
    val source: EntrySource,
    val createdAt: IsoInstant,
    val weekStart: IsoDate,
    val updatedAt: IsoInstant,
    val editable: Boolean,
)

/** `GET /v1/entries?weekStart=` (contract §6.2). Entries are newest first. */
@Serializable
data class EntryListDto(
    val weekStart: IsoDate,
    val weekEnd: IsoDate,
    val editable: Boolean,
    val entries: List<EntryDto>,
)

/** `PUT /v1/entries/{id}` body (contract §6.3). An update must send the original `createdAt`. */
@Serializable
data class EntryPutRequest(
    val name: String,
    val subcategory: String,
    val quantity: Int,
    val source: EntrySource,
    val createdAt: IsoInstant,
)

/** One upsert in `POST /v1/entries/sync` (contract §6.5). */
@Serializable
data class SyncUpsertDto(
    val id: String,
    val name: String,
    val subcategory: String,
    val quantity: Int,
    val source: EntrySource,
    val createdAt: IsoInstant,
)

/** `POST /v1/entries/sync` body. At most 500 items in total (contract §6.5). */
@Serializable
data class SyncRequest(
    val upserts: List<SyncUpsertDto> = emptyList(),
    val deletes: List<String> = emptyList(),
)

@Serializable(with = SyncOpSerializer::class)
enum class SyncOp { UPSERT, DELETE, UNKNOWN }

object SyncOpSerializer : FallbackEnumSerializer<SyncOp>("SyncOp", SyncOp.entries.toTypedArray(), SyncOp.UNKNOWN)

/**
 * Per-item sync outcome (contract §6.5). [ERROR] means "keep the item and retry later".
 * A status this app doesn't know yet decodes to [UNKNOWN]; treat it like [ERROR].
 */
@Serializable(with = SyncStatusSerializer::class)
enum class SyncStatus { OK, WEEK_CLOSED, INVALID, INVALID_TIMESTAMP, CONFLICT, ERROR, UNKNOWN }

object SyncStatusSerializer :
    FallbackEnumSerializer<SyncStatus>("SyncStatus", SyncStatus.entries.toTypedArray(), SyncStatus.UNKNOWN)

/**
 * `entry` is the saved entry for an OK upsert, or the unchanged server copy for WEEK_CLOSED (so the app can revert).
 */
@Serializable
data class SyncResultDto(
    val id: String? = null,
    val op: SyncOp,
    val status: SyncStatus,
    val entry: EntryDto? = null,
    val message: String? = null,
)

/** One result per item, in request order: upserts first, then deletes. */
@Serializable
data class SyncResponse(val results: List<SyncResultDto>)
