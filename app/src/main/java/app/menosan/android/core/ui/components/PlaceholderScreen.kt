package app.menosan.android.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.menosan.android.R

/**
 * Stand-in for a screen another workstream hasn't built yet. Replace the call in `MenosanNavHost`.
 * Pass [onBack] = null for a top-level tab (no back button).
 */
@Composable
fun PlaceholderScreen(
    title: String,
    workstream: String,
    onBack: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MenosanLogo(size = 64.dp)
            Text(
                text = stringResource(R.string.placeholder_coming_soon, workstream),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
