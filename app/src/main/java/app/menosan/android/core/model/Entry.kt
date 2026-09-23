package app.menosan.android.core.model

import java.time.Instant
import java.time.LocalDate

/** Sync state of an entry as the UI shows it (chips "Synced", "To Sync", "Couldn't sync"). */
enum class EntrySyncStatus { SYNCED, PENDING, FAILED }

/** A waste entry as screens see it. [weekStart] is in Asia/Manila (plan §4). */
data class Entry(
    val id: String,
    val name: String,
    val category: WasteCategory,
    val subcategory: String,
    val quantity: Int,
    val source: EntrySource,
    val createdAt: Instant,
    val weekStart: LocalDate,
    val syncStatus: EntrySyncStatus,
    /** Short, user-safe reason from the last failed sync, if any. */
    val lastError: String? = null,
    /** True only while [weekStart] is the current week (SFR11). The server has the final say. */
    val editable: Boolean,
)

/**
 * What the user fills in (manual form or confirmed photo suggestion). The category is derived from [subcategory]
 * through the taxonomy. The id and `createdAt` are set by the repository at save time.
 */
data class EntryDraft(
    val name: String,
    val subcategory: String,
    val quantity: Int,
    val source: EntrySource,
)

/** Field rules shared by the form, the repository, and the server (contract §6.3). */
object EntryRules {
    const val NAME_MAX = 60
    const val QUANTITY_MIN = 1
    const val QUANTITY_MAX = 999
}
