package app.menosan.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.menosan.android.R
import app.menosan.android.core.ui.components.BannerTone
import app.menosan.android.core.ui.components.BrandLoading
import app.menosan.android.core.ui.components.GoogleButton
import app.menosan.android.core.ui.components.MenosanWordmark
import app.menosan.android.core.ui.components.MessageBanner
import app.menosan.android.core.ui.components.BackButton
import app.menosan.android.core.ui.theme.MenosanTheme

@Composable
fun CreateAccountScreen(
    onBack: (() -> Unit)?,
    onCreated: () -> Unit,
    onAlreadyHadAccount: () -> Unit,
    onLogIn: () -> Unit,
    viewModel: CreateAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.outcome) {
        when (state.outcome) {
            CreateAccountOutcome.Created -> onCreated()
            CreateAccountOutcome.AlreadyExisted -> onAlreadyHadAccount()
            null -> return@LaunchedEffect
        }
        viewModel.onOutcomeHandled()
    }

    CreateAccountContent(
        state = state,
        onBack = onBack,
        onConsentChange = viewModel::onConsentChange,
        onContinue = { viewModel.onContinue(context) },
        onUseDifferentAccount = viewModel::useDifferentAccount,
        onLogIn = onLogIn,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountContent(
    state: CreateAccountUiState,
    onBack: (() -> Unit)?,
    onConsentChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onUseDifferentAccount: () -> Unit,
    onLogIn: () -> Unit,
) {
    when (state.phase) {
        CreateAccountPhase.Creating -> {
            BrandLoading(
                title = stringResource(R.string.create_account_creating_title),
                subtitle = stringResource(
                    if (state.slowServer) R.string.sign_in_waking_server else R.string.create_account_creating_body,
                ),
                modifier = Modifier.safeDrawingPadding(),
            )
            return
        }
        CreateAccountPhase.SigningIn -> {
            BrandLoading(
                title = stringResource(R.string.sign_in_signing_in),
                subtitle = null,
                modifier = Modifier.safeDrawingPadding(),
            )
            return
        }
        CreateAccountPhase.Idle -> Unit
    }

    var showNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) BackButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
            MenosanWordmark(showTagline = false)
            Spacer(Modifier.weight(1f))
            if (onBack != null) Spacer(Modifier.size(40.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.create_account_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = state.signedInEmail?.let { stringResource(R.string.create_account_signed_in_as, it) }
                    ?: stringResource(R.string.create_account_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PrivacyCard(onReadMore = { showNotice = true })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = state.consentChecked, role = Role.Checkbox, onValueChange = onConsentChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = state.consentChecked, onCheckedChange = null)
            Text(
                text = stringResource(R.string.create_account_consent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        if (state.signedInEmail == null) {
            GoogleButton(onClick = onContinue, enabled = state.canSubmit)
        } else {
            Button(
                onClick = onContinue,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(stringResource(R.string.create_account_button), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (!state.consentChecked) {
            Text(
                text = stringResource(R.string.create_account_consent_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.errorRes?.let {
            MessageBanner(
                title = stringResource(R.string.create_account_error_title),
                text = stringResource(it) + (state.requestId?.let { id -> "\n" + stringResource(R.string.error_request_id, id) } ?: ""),
                tone = BannerTone.Error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.signedInEmail == null) {
                Text(stringResource(R.string.create_account_have_account), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onLogIn) { Text(stringResource(R.string.welcome_log_in)) }
            } else {
                TextButton(onClick = onUseDifferentAccount) { Text(stringResource(R.string.create_account_use_different)) }
            }
        }
    }

    if (showNotice) {
        ModalBottomSheet(onDismissRequest = { showNotice = false }) {
            PrivacyNotice(Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp))
        }
    }
}

/** The short privacy notice (plan §10 Create account), styled like the mockup's "Getting Started" card. */
@Composable
private fun PrivacyCard(onReadMore: () -> Unit) {
    val onCard = MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium, color = onCard)
        PrivacyLine(Icons.Outlined.Lock, R.string.privacy_short_private)
        PrivacyLine(Icons.Outlined.CameraAlt, R.string.privacy_short_photos)
        PrivacyLine(Icons.Outlined.FileDownload, R.string.privacy_short_control)
        TextButton(onClick = onReadMore, colors = ButtonDefaults.textButtonColors(contentColor = onCard)) {
            Text(stringResource(R.string.privacy_read_full))
        }
    }
}

@Composable
private fun PrivacyLine(icon: ImageVector, text: Int) {
    val onCard = MaterialTheme.colorScheme.onPrimaryContainer
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = onCard, modifier = Modifier.size(22.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = onCard)
    }
}

/** The full notice: what is stored, AI processing, nothing public, export and delete. */
@Composable
fun PrivacyNotice(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleLarge)
        listOf(
            R.string.privacy_what_we_store,
            R.string.privacy_photos,
            R.string.privacy_ai,
            R.string.privacy_nothing_public,
            R.string.privacy_your_control,
        ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateAccountContentPreview() {
    MenosanTheme {
        CreateAccountContent(
            state = CreateAccountUiState(),
            onBack = {},
            onConsentChange = {},
            onContinue = {},
            onUseDifferentAccount = {},
            onLogIn = {},
        )
    }
}
