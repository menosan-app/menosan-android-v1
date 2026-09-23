package app.menosan.android.feature.photo

import app.menosan.android.core.model.EntryDraft
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.Taxonomy
import app.menosan.android.data.remote.dto.PhotoSuggestionDto
import app.menosan.android.feature.logging.EntryField
import app.menosan.android.feature.logging.EntryFormState

/**
 * The review form after a photo analysis (plan §10 Photo logging, NFR10, SFR9.2–9.5): the shared entry form
 * prefilled from the suggestion, the fields still marked as AI suggestions, and the required confirmation.
 */
data class PhotoReview(
    val form: EntryFormState,
    /** Fields that still show the AI tint and chip. A field leaves when the user edits or confirms it. */
    val aiSuggested: Set<EntryField>,
    /** The server's SFR9.5 warning, shown next to the form. */
    val warning: String,
    /** "I checked these details" (SFR9.4). Saving needs it. */
    val confirmed: Boolean = false,
) {
    /** Applies a form change and unmarks every field whose value changed (e.g. a new category also clears the subcategory). */
    fun edit(next: EntryFormState): PhotoReview = copy(form = next, aiSuggested = aiSuggested - changedFields(form, next))

    /** The user tapped a field's "AI suggestion" chip to accept it as is. */
    fun confirmField(field: EntryField): PhotoReview = copy(aiSuggested = aiSuggested - field)

    fun withConfirmed(value: Boolean): PhotoReview = copy(confirmed = value)

    /** The draft to save (always `source = PHOTO`), or null while the form is invalid. */
    fun toDraft(taxonomy: Taxonomy): EntryDraft? = form.toDraft(taxonomy, EntrySource.PHOTO)

    companion object {
        fun from(suggestion: PhotoSuggestionDto, warning: String, taxonomy: Taxonomy): PhotoReview {
            val draft = EntryDraft(suggestion.name, suggestion.subcategory, suggestion.quantity, EntrySource.PHOTO)
            val form = EntryFormState.from(draft, taxonomy).let {
                // If this phone's taxonomy doesn't know the code yet, keep the server's category.
                if (it.category == null) it.copy(category = suggestion.category) else it
            }.withName(suggestion.name)
            return PhotoReview(form = form, aiSuggested = EntryField.entries.toSet(), warning = warning)
        }

        fun changedFields(old: EntryFormState, new: EntryFormState): Set<EntryField> = buildSet {
            if (old.category != new.category) add(EntryField.CATEGORY)
            if (old.subcategory != new.subcategory) add(EntryField.SUBCATEGORY)
            if (old.name != new.name) add(EntryField.NAME)
            if (old.quantity != new.quantity) add(EntryField.QUANTITY)
        }
    }
}
