package app.menosan.android.feature.logging

import app.menosan.android.core.model.EntryDraft
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.data.repo.TaxonomyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EntryFormStateTest {
    private val taxonomy = TaxonomyRepository.parseTaxonomy(File("src/main/assets/taxonomy.json").readText())

    private val valid = EntryFormState(
        category = WasteCategory.RESIDUAL,
        subcategory = "RES_SACHETS",
        name = "  Coffee 3-in-1 sachet ",
        quantity = "3",
    )

    @Test
    fun `a valid form becomes a trimmed draft`() {
        assertTrue(valid.errors(taxonomy).isEmpty())
        assertEquals(EntryDraft("Coffee 3-in-1 sachet", "RES_SACHETS", 3, EntrySource.MANUAL), valid.toDraft(taxonomy, EntrySource.MANUAL))
    }

    @Test
    fun `an empty form reports every field`() {
        assertEquals(EntryField.entries.toSet(), EntryFormState().errors(taxonomy).keys)
        assertNull(EntryFormState().toDraft(taxonomy, EntrySource.MANUAL))
    }

    @Test
    fun `quantity must be 1 to 999`() {
        assertTrue(EntryField.QUANTITY in valid.copy(quantity = "0").errors(taxonomy))
        assertTrue(valid.copy(quantity = "999").errors(taxonomy).isEmpty())
        assertEquals("999", valid.withQuantity("99912").quantity)
        assertEquals("12", valid.withQuantity("1a2").quantity)
    }

    @Test
    fun `name is capped at 60 characters and must not be blank`() {
        assertEquals(60, valid.withName("x".repeat(80)).name.length)
        assertTrue(EntryField.NAME in valid.copy(name = "   ").errors(taxonomy))
    }

    @Test
    fun `changing category clears a subcategory from another category`() {
        val changed = valid.withCategory(WasteCategory.RECYCLABLE)
        assertNull(changed.subcategory)
        assertEquals(valid, valid.withCategory(WasteCategory.RESIDUAL))
    }

    @Test
    fun `picking a subcategory sets its category`() {
        val picked = EntryFormState().withSubcategory("REC_PET_BOTTLES", taxonomy)
        assertEquals(WasteCategory.RECYCLABLE, picked.category)
    }

    @Test
    fun `a subcategory that doesn't match the category is an error`() {
        assertTrue(EntryField.SUBCATEGORY in valid.copy(category = WasteCategory.BIODEGRADABLE).errors(taxonomy))
    }

    @Test
    fun `from draft round-trips`() {
        val draft = EntryDraft("Plastic bottle", "REC_PET_BOTTLES", 2, EntrySource.PHOTO)
        assertEquals(draft, EntryFormState.from(draft, taxonomy).toDraft(taxonomy, EntrySource.PHOTO))
    }
}
