package app.menosan.android.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.menosan.android.R
import app.menosan.android.core.ui.theme.MenosanTheme

/** The two-leaf Menosan mark. Decorative: screens name themselves in text. */
@Composable
fun MenosanLogo(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Image(
        painter = painterResource(R.drawable.ic_menosan_logo),
        contentDescription = null,
        modifier = modifier.size(width = size * (167f / 183f), height = size),
    )
}

/** Logo + "menosan" + tagline, as in the Home header. */
@Composable
fun MenosanWordmark(modifier: Modifier = Modifier, showTagline: Boolean = true) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        MenosanLogo(size = 36.dp)
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                text = stringResource(R.string.brand_wordmark),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (showTagline) {
                Text(
                    text = stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The circular outlined back button of the mockups. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Back button on the left and a centered bold title (sub-screens). */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) BackButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
    }
}

enum class BannerTone { Calm, Error }

/** Rounded full-width message block: calm (sage) for info and offline notes, error (rust) for failures. */
@Composable
fun MessageBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: BannerTone = BannerTone.Calm,
    icon: ImageVector = if (tone == BannerTone.Error) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
) {
    val (background, content) = when (tone) {
        BannerTone.Calm -> MenosanTheme.colors.calm to MenosanTheme.colors.onCalm
        BannerTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Column {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = content)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}

/** Full-screen brand loading state: the logo, a bold title, and a one-line subtitle. */
@Composable
fun BrandLoading(title: String?, subtitle: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MenosanLogo(size = 120.dp)
        if (title != null) {
            Spacer(Modifier.heightIn(min = 24.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
        if (subtitle != null) {
            Spacer(Modifier.heightIn(min = 8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.heightIn(min = 32.dp))
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

/** "Continue with Google": outlined, with the Google G (Google sign-in branding). */
@Composable
fun GoogleButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = MenosanTheme.colors.card,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.sign_in_continue_with_google), style = MaterialTheme.typography.titleMedium)
    }
}

/** A pill-shaped status or highlight label ("Top Hotspot", "Synced", "To Sync"). */
@Composable
fun Pill(text: String, background: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .background(background, MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}
