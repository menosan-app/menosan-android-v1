package app.menosan.android.feature.photo

import app.menosan.android.core.model.EntryDraft
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.data.remote.dto.PhotoSuggestionDto
import app.menosan.android.data.repo.TaxonomyRepository
import app.menosan.android.feature.logging.EntryField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhotoReviewTest {
    private val taxonomy = TaxonomyRepository.parseTaxonomy(File("src/main/assets/taxonomy.json").readText())
    private val suggestion = PhotoSuggestionDto("Coffee 3-in-1 sachet", WasteCategory.RESIDUAL, "RES_SACHETS", 5, 0.82)
    private val review = PhotoReview.from(suggestion, "AI can be wrong.", taxonomy)

    @Test
    fun `the suggestion prefills the form with every field marked`() {
        assertEquals(WasteCategory.RESIDUAL, review.form.category)
        assertEquals("RES_SACHETS", review.form.subcategory)
        assertEquals("Coffee 3-in-1 sachet", review.form.name)
        assertEquals("5", review.form.quantity)
        assertEquals(EntryField.entries.toSet(), review.aiSuggested)
        assertEquals("AI can be wrong.", review.warning)
        assertFalse(review.confirmed)
    }

    @Test
    fun `editing a field unmarks only that field`() {
        val edited = review.edit(review.form.withName("Coffee sachet"))
        assertEquals(setOf(EntryField.CATEGORY, EntryField.SUBCATEGORY, EntryField.QUANTITY), edited.aiSuggested)

        val quantity = edited.edit(edited.form.withQuantity("6"))
        assertEquals(setOf(EntryField.CATEGORY, EntryField.SUBCATEGORY), quantity.aiSuggested)
    }

    @Test
    fun `a change that leaves the value the same keeps the mark`() {
        val same = review.edit(review.form.withCategory(WasteCategory.RESIDUAL))
        assertEquals(EntryField.entries.toSet(), same.aiSuggested)
    }

    @Test
    fun `a new category also clears and unmarks the subcategory`() {
        val edited = review.edit(review.form.withCategory(WasteCategory.RECYCLABLE))
        assertNull(edited.form.subcategory)
        assertEquals(setOf(EntryField.NAME, EntryField.QUANTITY), edited.aiSuggested)
    }

    @Test
    fun `a subcategory from another category unmarks both`() {
        val edited = review.edit(review.form.withSubcategory("REC_PET_BOTTLES", taxonomy))
        assertEquals(WasteCategory.RECYCLABLE, edited.form.category)
        assertEquals(setOf(EntryField.NAME, EntryField.QUANTITY), edited.aiSuggested)
    }

    @Test
    fun `tapping a chip confirms that field only`() {
        val confirmed = review.confirmField(EntryField.QUANTITY).confirmField(EntryField.NAME)
        assertEquals(setOf(EntryField.CATEGORY, EntryField.SUBCATEGORY), confirmed.aiSuggested)
        assertEquals(review.form, confirmed.form)
    }

    @Test
    fun `the draft is always a photo entry`() {
        assertEquals(EntryDraft("Coffee 3-in-1 sachet", "RES_SACHETS", 5, EntrySource.PHOTO), review.toDraft(taxonomy))
        assertNull(review.edit(review.form.withQuantity("")).toDraft(taxonomy))
    }

    @Test
    fun `an unknown code keeps the server's category`() {
        val unknown = PhotoReview.from(suggestion.copy(subcategory = "RES_NEW_CODE"), "w", taxonomy)
        assertEquals(WasteCategory.RESIDUAL, unknown.form.category)
        assertTrue(EntryField.SUBCATEGORY in unknown.form.errors(taxonomy))
    }
}
