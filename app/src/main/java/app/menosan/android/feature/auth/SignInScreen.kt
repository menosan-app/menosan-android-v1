package app.menosan.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.menosan.android.R
import app.menosan.android.core.ui.components.BannerTone
import app.menosan.android.core.ui.components.BrandLoading
import app.menosan.android.core.ui.components.GoogleButton
import app.menosan.android.core.ui.components.MenosanLogo
import app.menosan.android.core.ui.components.MessageBanner
import app.menosan.android.core.ui.components.ScreenHeader
import app.menosan.android.core.ui.theme.MenosanTheme

/** "Log in" (mockup `Login`): Google sign-in, then `GET /v1/me` decides dashboard or create account. */
@Composable
fun SignInScreen(
    onBack: (() -> Unit)?,
    onSignedIn: () -> Unit,
    onNeedsAccount: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.destination) {
        when (state.destination) {
            SignInDestination.Dashboard -> onSignedIn()
            SignInDestination.CreateAccount -> onNeedsAccount()
            null -> return@LaunchedEffect
        }
        viewModel.onNavigated()
    }

    SignInContent(
        state = state,
        onBack = onBack,
        onContinue = { viewModel.signIn(context) },
        onCreateAccount = onCreateAccount,
    )
}

@Composable
fun SignInContent(
    state: SignInUiState,
    onBack: (() -> Unit)?,
    onContinue: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    if (state.busy) {
        BrandLoading(
            title = stringResource(
                if (state.phase == SignInPhase.CheckingAccount) R.string.sign_in_checking_account else R.string.sign_in_signing_in,
            ),
            subtitle = if (state.slowServer) stringResource(R.string.sign_in_waking_server) else null,
            modifier = Modifier.safeDrawingPadding(),
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(title = stringResource(R.string.sign_in_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.heightIn(min = 32.dp))
            MenosanLogo(size = 140.dp)
            Text(
                text = stringResource(R.string.sign_in_welcome_back),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.heightIn(min = 32.dp))
            GoogleButton(onClick = onContinue)
            state.errorRes?.let { error ->
                MessageBanner(
                    title = stringResource(R.string.sign_in_error_title),
                    text = stringResource(error) +
                        (state.requestId?.let { "\n" + stringResource(R.string.error_request_id, it) } ?: ""),
                    tone = BannerTone.Error,
                )
            }
            Text(
                text = stringResource(R.string.sign_in_needs_internet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.sign_in_new_here), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCreateAccount) {
                Text(stringResource(R.string.welcome_create_account), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInContentPreview() {
    MenosanTheme {
        SignInContent(
            state = SignInUiState(errorRes = R.string.sign_in_error_generic),
            onBack = {},
            onContinue = {},
            onCreateAccount = {},
        )
    }
}
