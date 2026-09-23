package app.menosan.android.core.analytics

import app.menosan.android.core.model.Taxonomy

// Android-only glue for the ported analytics (Analytics.kt stays identical to the backend).

/** The taxonomy reduced to what the pure analytics functions need (mirrors the backend's `analyticsTaxonomy()`). */
fun Taxonomy.analyticsTaxonomy(): Map<String, SubcategoryInfo> =
    subcategories.associate { it.code to SubcategoryInfo(it.code, it.category.name, it.avoidable) }
