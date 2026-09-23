package app.menosan.android.data.repo

import app.menosan.android.core.model.WasteCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled `assets/taxonomy.json` parses and matches plan §3. It must stay identical to
 * `menosan-api/src/main/resources/taxonomy.json` (compare with `diff` when either changes).
 */
class TaxonomyAssetTest {
    private val taxonomy = TaxonomyRepository.parseTaxonomy(File("src/main/assets/taxonomy.json").readText())

    @Test
    fun `has the four main categories, only SPECIAL not analyzed`() {
        assertEquals(WasteCategory.entries.toList(), taxonomy.categories.map { it.code })
        assertEquals(listOf(WasteCategory.SPECIAL), taxonomy.categories.filterNot { it.analyzed }.map { it.code })
        assertEquals("Asia/Manila", taxonomy.timezone)
    }

    @Test
    fun `has the 25 subcategories of plan section 3 with unique codes`() {
        assertEquals(25, taxonomy.subcategories.size)
        assertEquals(25, taxonomy.subcategories.map { it.code }.toSet().size)
        assertEquals(5, taxonomy.subcategoriesOf(WasteCategory.BIODEGRADABLE).size)
        assertEquals(6, taxonomy.subcategoriesOf(WasteCategory.RECYCLABLE).size)
        assertEquals(8, taxonomy.subcategoriesOf(WasteCategory.RESIDUAL).size)
        assertEquals(6, taxonomy.subcategoriesOf(WasteCategory.SPECIAL).size)
    }

    @Test
    fun `code prefixes match their category and SPECIAL is never avoidable`() {
        val prefix = mapOf(
            WasteCategory.BIODEGRADABLE to "BIO_",
            WasteCategory.RECYCLABLE to "REC_",
            WasteCategory.RESIDUAL to "RES_",
            WasteCategory.SPECIAL to "SPC_",
        )
        taxonomy.subcategories.forEach { assertTrue(it.code, it.code.startsWith(prefix.getValue(it.category))) }
        assertFalse(taxonomy.subcategoriesOf(WasteCategory.SPECIAL).any { it.avoidable })
    }

    @Test
    fun `avoidable flags match plan section 3`() {
        val avoidable = taxonomy.subcategories.filter { it.avoidable }.map { it.code }.toSet()
        assertEquals(
            setOf(
                "BIO_FOOD_LEFTOVERS", "BIO_SPOILED_FOOD",
                "REC_PET_BOTTLES", "REC_RIGID_PLASTICS", "REC_PAPER_CARDBOARD", "REC_GLASS",
                "RES_SACHETS", "RES_PLASTIC_BAGS", "RES_SNACK_WRAPPERS", "RES_STYROFOAM", "RES_DISPOSABLES", "RES_TISSUE",
            ),
            avoidable,
        )
    }

    @Test
    fun `lookup by code works`() {
        assertEquals(WasteCategory.RESIDUAL, taxonomy.subcategory("RES_SACHETS")?.category)
        assertEquals(null, taxonomy.subcategory("NOPE"))
    }
}
