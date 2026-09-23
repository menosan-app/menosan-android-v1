package app.menosan.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.menosan.android.R
import app.menosan.android.core.ui.theme.MenosanTheme

@Composable
fun CreateAccountScreen(
    onCreated: () -> Unit,
    viewModel: CreateAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.created) {
        if (state.created) onCreated()
    }

    CreateAccountContent(
        state = state,
        onConsentChange = viewModel::onConsentChange,
        onCreate = viewModel::createAccount,
        onUseDifferentAccount = viewModel::useDifferentAccount,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountContent(
    state: CreateAccountUiState,
    onConsentChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onUseDifferentAccount: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.create_account_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.email?.let {
                Text(
                    text = stringResource(R.string.create_account_signed_in_as, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
                    PrivacyPoint(R.string.privacy_what_we_store)
                    PrivacyPoint(R.string.privacy_photos)
                    PrivacyPoint(R.string.privacy_ai)
                    PrivacyPoint(R.string.privacy_nothing_public)
                    PrivacyPoint(R.string.privacy_your_control)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(
                        value = state.consentChecked,
                        enabled = !state.submitting,
                        role = Role.Checkbox,
                        onValueChange = onConsentChange,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.consentChecked, onCheckedChange = null, enabled = !state.submitting)
                Text(
                    text = stringResource(R.string.create_account_consent),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            if (state.submitting) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    if (state.slowServer) {
                        Text(
                            text = stringResource(R.string.sign_in_waking_server),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Button(
                    onClick = onCreate,
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.create_account_button))
                }
            }

            state.errorRes?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.requestId?.let {
                Text(
                    text = stringResource(R.string.error_request_id, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(
                onClick = onUseDifferentAccount,
                enabled = !state.submitting,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.create_account_use_different))
            }
        }
    }
}

@Composable
private fun PrivacyPoint(textRes: Int) {
    Text(text = stringResource(textRes), style = MaterialTheme.typography.bodyMedium)
}

@Preview(showBackground = true)
@Composable
private fun CreateAccountScreenPreview() {
    MenosanTheme {
        CreateAccountContent(
            state = CreateAccountUiState(email = "juan@example.com"),
            onConsentChange = {},
            onCreate = {},
            onUseDifferentAccount = {},
        )
    }
}
