package app.menosan.android.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Main waste categories (plan §3). SPECIAL is logged but never analyzed (I3). */
@Serializable
enum class WasteCategory { BIODEGRADABLE, RECYCLABLE, RESIDUAL, SPECIAL }

/** How an entry was logged. */
@Serializable
enum class EntrySource { MANUAL, PHOTO }

@Serializable
data class TaxonomyCategory(val code: WasteCategory, val label: String, val analyzed: Boolean)

@Serializable
data class TaxonomySubcategory(
    val code: String,
    val category: WasteCategory,
    val label: String,
    val examples: List<String>,
    val avoidable: Boolean,
    val sortOrder: Int,
)

/**
 * The taxonomy exactly as `assets/taxonomy.json` and `GET /v1/taxonomy` serve it (plan §3, contract §5.3).
 * Codes are stable identifiers. Labels may change.
 */
@Serializable
data class Taxonomy(
    val version: Int,
    val timezone: String? = null,
    val categories: List<TaxonomyCategory>,
    val subcategories: List<TaxonomySubcategory>,
) {
    @Transient
    private val byCode: Map<String, TaxonomySubcategory> = subcategories.associateBy { it.code }

    fun subcategory(code: String): TaxonomySubcategory? = byCode[code]

    fun category(code: WasteCategory): TaxonomyCategory? = categories.firstOrNull { it.code == code }

    /** Subcategories of one main category, in display order. */
    fun subcategoriesOf(category: WasteCategory): List<TaxonomySubcategory> =
        subcategories.filter { it.category == category }.sortedBy { it.sortOrder }
}
