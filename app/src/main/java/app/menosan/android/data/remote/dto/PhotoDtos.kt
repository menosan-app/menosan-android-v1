package app.menosan.android.data.remote.dto

import app.menosan.android.core.model.WasteCategory
import kotlinx.serialization.Serializable

/**
 * An AI suggestion (contract §7.1). It always passes entry validation unchanged, but the app must still
 * mark every field as AI-suggested until the user edits or confirms it (NFR10).
 */
@Serializable
data class PhotoSuggestionDto(
    val name: String,
    val category: WasteCategory,
    val subcategory: String,
    val quantity: Int,
    val confidence: Double,
)

/** `POST /v1/photo-analysis` `200`. Show [warning] next to the review form (SFR9.5). */
@Serializable
data class PhotoAnalysisDto(val suggestion: PhotoSuggestionDto, val warning: String)
