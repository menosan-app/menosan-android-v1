package app.menosan.android.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.menosan.android.R

private fun roboto(weight: FontWeight) = Font(
    resId = R.font.roboto,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Bundled Roboto (variable font, OFL), so every phone renders the same type, offline too. */
val Roboto = FontFamily(
    roboto(FontWeight.Normal),
    roboto(FontWeight.Medium),
    roboto(FontWeight.SemiBold),
    roboto(FontWeight.Bold),
)

private val base = Typography()

private fun TextStyle.roboto(weight: FontWeight? = null) = copy(fontFamily = Roboto, fontWeight = weight ?: fontWeight)

/** Regular Roboto for body text; bolder weights of the same family for titles and big numbers (DESIGN.md §2). */
val MenosanTypography = Typography(
    displayLarge = base.displayLarge.roboto(FontWeight.Bold),
    displayMedium = base.displayMedium.roboto(FontWeight.Bold),
    displaySmall = base.displaySmall.roboto(FontWeight.Bold),
    headlineLarge = base.headlineLarge.roboto(FontWeight.Bold),
    headlineMedium = base.headlineMedium.roboto(FontWeight.Bold),
    headlineSmall = base.headlineSmall.roboto(FontWeight.SemiBold),
    titleLarge = base.titleLarge.roboto(FontWeight.SemiBold),
    titleMedium = base.titleMedium.roboto(FontWeight.SemiBold),
    titleSmall = base.titleSmall.roboto(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.roboto(),
    bodyMedium = base.bodyMedium.roboto(),
    bodySmall = base.bodySmall.roboto(),
    labelLarge = base.labelLarge.roboto(FontWeight.SemiBold),
    labelMedium = base.labelMedium.roboto(FontWeight.Medium),
    labelSmall = base.labelSmall.roboto(FontWeight.Medium),
)
