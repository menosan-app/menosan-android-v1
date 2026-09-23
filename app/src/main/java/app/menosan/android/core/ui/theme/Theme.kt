package app.menosan.android.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Fixed brand colors (docs/DESIGN.md §2). No dynamic color, so every tester sees the same app.
//
// Brand fills (FAB, hero card, selected tiles and tabs) use `primaryContainer` = Moss in both modes, as in the
// designer's schemes. In dark mode `primary` is Moss Light, because Moss text or icons on Deep Forest are unreadable.

private val LightColors = lightColorScheme(
    primary = Moss,
    onPrimary = Color.White,
    primaryContainer = Moss,
    onPrimaryContainer = Color.White,
    secondary = Sage,
    onSecondary = Ink,
    secondaryContainer = SageLight,
    onSecondaryContainer = Moss,
    tertiary = Ochre,
    onTertiary = Color.White,
    tertiaryContainer = Sand,
    onTertiaryContainer = Color(0xFF4A3310),
    error = Rust,
    onError = Color.White,
    errorContainer = RustContainer,
    onErrorContainer = Color(0xFF7A2A1A),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E5DA),
    onSurfaceVariant = MutedText,
    outline = Color(0xFF55675D),
    outlineVariant = Color(0xFFCBD1C6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF9F4),
    surfaceContainer = Color(0xFFF1EEE4),
    surfaceContainerHigh = Color(0xFFECE8DD),
    surfaceContainerHighest = Color(0xFFE6E2D6),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = MossLight,
    onPrimary = DeepForest,
    primaryContainer = Moss,
    onPrimaryContainer = Paper,
    secondary = Sage,
    onSecondary = DeepForest,
    secondaryContainer = Color(0xFF34443A),
    onSecondaryContainer = Paper,
    tertiary = OchreLight,
    onTertiary = DeepForest,
    tertiaryContainer = Color(0xFF5E4520),
    onTertiaryContainer = OchreLight,
    error = Color(0xFFE08A76),
    onError = DeepForest,
    errorContainer = Color(0xFF5A2A20),
    onErrorContainer = Color(0xFFF3C9BE),
    background = DeepForest,
    onBackground = Paper,
    surface = DeepForest,
    onSurface = Paper,
    surfaceVariant = ForestGray,
    onSurfaceVariant = Mist,
    outline = MutedSage,
    outlineVariant = Color(0xFF353D37),
    surfaceContainerLowest = Color(0xFF121614),
    surfaceContainerLow = Color(0xFF1A201C),
    surfaceContainer = ForestGray,
    surfaceContainerHigh = Color(0xFF252D28),
    surfaceContainerHighest = Color(0xFF2E3731),
    scrim = Color(0xFF000000),
)

/** Colors Material 3 has no role for: per-category chart colors and the status chip and banner colors. */
@Immutable
data class MenosanColors(
    val biodegradable: Color,
    val recyclable: Color,
    val residual: Color,
    /** Logged but excluded from analysis (I3). */
    val special: Color,
    /** "Synced" chip, offline and info banners. */
    val calm: Color,
    val onCalm: Color,
    /** "To Sync" chip and intervention tags. */
    val pending: Color,
    val onPending: Color,
    /** "Top Hotspot" and "New" chips. */
    val highlight: Color,
    val onHighlight: Color,
    /** Soft info card background ("You're in" features). */
    val mist: Color,
    /** Cards on the page background. */
    val card: Color,
)

private val LightMenosanColors = MenosanColors(
    biodegradable = Moss,
    recyclable = Ochre,
    residual = Antique,
    special = Color(0xFF6E7A70),
    calm = Sage,
    onCalm = Color(0xFF1C3A30),
    pending = Sand,
    onPending = Color(0xFF6A4A1C),
    highlight = Ochre,
    onHighlight = Color.White,
    mist = SageMist,
    card = Color.White,
)

private val DarkMenosanColors = MenosanColors(
    biodegradable = MossLight,
    recyclable = OchreLight,
    residual = AntiqueLight,
    special = Mist,
    calm = Color(0xFF3C5446),
    onCalm = Paper,
    pending = Color(0xFF6B5227),
    onPending = OchreLight,
    highlight = Ochre,
    onHighlight = DeepForest,
    mist = Color(0xFF243029),
    card = ForestGray,
)

private val LocalMenosanColors = staticCompositionLocalOf { LightMenosanColors }

private val MenosanShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object MenosanTheme {
    val colors: MenosanColors
        @Composable @ReadOnlyComposable
        get() = LocalMenosanColors.current
}

@Composable
fun MenosanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMenosanColors provides if (darkTheme) DarkMenosanColors else LightMenosanColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MenosanTypography,
            shapes = MenosanShapes,
            content = content,
        )
    }
}
