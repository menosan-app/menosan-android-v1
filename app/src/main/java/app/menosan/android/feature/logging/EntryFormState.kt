package app.menosan.android.feature.logging

import androidx.annotation.StringRes
import app.menosan.android.R
import app.menosan.android.core.model.EntryDraft
import app.menosan.android.core.model.EntryRules
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.Taxonomy
import app.menosan.android.core.model.WasteCategory

/** The fields of the entry form, used for errors and for AI-suggestion marking (NFR10). */
enum class EntryField { CATEGORY, SUBCATEGORY, NAME, QUANTITY }

/**
 * State of the shared entry form (manual logging, editing, and photo review). Immutable; screens keep it in their
 * ViewModel and replace it on every change.
 */
data class EntryFormState(
    val category: WasteCategory? = null,
    val subcategory: String? = null,
    val name: String = "",
    /** Raw text so the user can clear the field while typing. */
    val quantity: String = "",
) {
    /** Picking another category clears a subcategory that no longer fits. */
    fun withCategory(value: WasteCategory): EntryFormState =
        if (value == category) this else copy(category = value, subcategory = null)

    fun withSubcategory(code: String, taxonomy: Taxonomy): EntryFormState =
        copy(subcategory = code, category = taxonomy.subcategory(code)?.category ?: category)

    fun withName(value: String): EntryFormState = copy(name = value.take(EntryRules.NAME_MAX))

    fun withQuantity(value: String): EntryFormState = copy(quantity = value.filter(Char::isDigit).take(3))

    /** Field → message. Empty when the form can be saved. Same rules as the server (contract §6.3). */
    fun errors(taxonomy: Taxonomy): Map<EntryField, Int> = buildMap {
        if (category == null) put(EntryField.CATEGORY, R.string.entry_error_category)
        val sub = subcategory?.let(taxonomy::subcategory)
        if (sub == null || (category != null && sub.category != category)) put(EntryField.SUBCATEGORY, R.string.entry_error_subcategory)
        if (name.isBlank()) put(EntryField.NAME, R.string.entry_error_name)
        val qty = quantity.toIntOrNull()
        if (qty == null || qty !in EntryRules.QUANTITY_MIN..EntryRules.QUANTITY_MAX) put(EntryField.QUANTITY, R.string.entry_error_quantity)
    }

    /** The draft to save, or null while [errors] isn't empty. */
    fun toDraft(taxonomy: Taxonomy, source: EntrySource): EntryDraft? {
        if (errors(taxonomy).isNotEmpty()) return null
        return EntryDraft(name = name.trim(), subcategory = subcategory!!, quantity = quantity.toInt(), source = source)
    }

    companion object {
        fun from(draft: EntryDraft, taxonomy: Taxonomy) = EntryFormState(
            category = taxonomy.subcategory(draft.subcategory)?.category,
            subcategory = draft.subcategory,
            name = draft.name,
            quantity = draft.quantity.toString(),
        )
    }
}

@StringRes
fun WasteCategory.labelRes(): Int = when (this) {
    WasteCategory.BIODEGRADABLE -> R.string.category_biodegradable
    WasteCategory.RECYCLABLE -> R.string.category_recyclable
    WasteCategory.RESIDUAL -> R.string.category_residual
    WasteCategory.SPECIAL -> R.string.category_special
}
