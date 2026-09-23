package app.menosan.android.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.menosan.android.R
import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.core.ui.theme.PaperLight
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AccountReadyViewModel @Inject constructor(auth: AuthService) : ViewModel() {
    val firstName: String? = auth.currentUser?.displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
}

/** Shown once after a new account is created (mockup `LoadingScreen AfterAccountCreation`). */
@Composable
fun AccountReadyScreen(
    onGetStarted: () -> Unit,
    viewModel: AccountReadyViewModel = hiltViewModel(),
) {
    AccountReadyContent(firstName = viewModel.firstName, onGetStarted = onGetStarted)
}

@Composable
fun AccountReadyContent(firstName: String?, onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Text(
            text = stringResource(R.string.ready_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = firstName?.let { stringResource(R.string.ready_title_name, it) } ?: stringResource(R.string.ready_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.ready_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Image(
            painter = painterResource(R.drawable.illustration_ready),
            contentDescription = null,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .background(PaperLight, MaterialTheme.shapes.large),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MenosanTheme.colors.mist, MaterialTheme.shapes.medium)
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ReadyFeature(Icons.Outlined.Home, R.string.ready_log_title, R.string.ready_log_body, Modifier.weight(1f))
            ReadyFeature(Icons.AutoMirrored.Outlined.ReceiptLong, R.string.ready_dashboard_title, R.string.ready_dashboard_body, Modifier.weight(1f))
            ReadyFeature(Icons.Outlined.BarChart, R.string.ready_insights_title, R.string.ready_insights_body, Modifier.weight(1f))
        }
        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text(stringResource(R.string.ready_get_started), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ReadyFeature(icon: ImageVector, title: Int, body: Int, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(22.dp))
        }
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountReadyPreview() {
    MenosanTheme { AccountReadyContent(firstName = "Liza", onGetStarted = {}) }
}
