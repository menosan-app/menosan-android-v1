package app.menosan.android.feature.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.menosan.android.R
import app.menosan.android.core.ui.components.BannerTone
import app.menosan.android.core.ui.components.MenosanLogo
import app.menosan.android.core.ui.components.MessageBanner
import app.menosan.android.core.ui.theme.MenosanTheme
import kotlinx.coroutines.delay

/** White card with a thin outline (DESIGN.md §3 "Cards"). */
@Composable
fun ReportCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MenosanTheme.colors.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A horizontal bar (Compose Canvas, no chart library): [fraction] of the track filled with [color]. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)
        val filled = size.width * fraction.coerceIn(0f, 1f)
        if (filled > 0f) drawRoundRect(color = color, size = Size(maxOf(filled, size.height), size.height), cornerRadius = radius)
    }
}

/**
 * Patient loading for a server that may be asleep (Render Free, ~60 s): the logo, a line, and after 5 s a note that
 * the server is waking up.
 */
@Composable
fun PatientLoading(title: String, modifier: Modifier = Modifier) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5_000)
        slow = true
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MenosanLogo(size = 72.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        if (slow) {
            Text(
                stringResource(R.string.reports_server_waking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Centered supportive message with the logo, for empty and not-found states. */
@Composable
fun GentleMessage(title: String?, body: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MenosanLogo(size = 72.dp)
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Error banner plus a retry button, when there's nothing cached to show. */
@Composable
fun ProblemWithRetry(problem: ReportProblem, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MessageBanner(text = problemText(problem), tone = BannerTone.Error)
        OutlinedButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.reports_retry))
        }
    }
}
