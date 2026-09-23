package app.menosan.android.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.menosan.android.R
import app.menosan.android.core.settings.ThemeMode
import app.menosan.android.core.ui.components.Pill
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.feature.auth.PrivacyNotice

/** Profile tab (mockups `Profile`, `Profile LightDark`, `Profile ExportPopUp`, `Profile DeletePopUp`; style only). */
@Composable
fun ProfileRoute(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE)) { uri ->
        viewModel.onExportDestination(uri?.toString())
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ProfileEvent.PickExportFile -> exportPicker.launch(event.fileName)
                is ProfileEvent.Message -> snackbar.showSnackbar(resources.getString(event.text))
                // The nav host returns to Welcome once signed out, so these outlive the screen as toasts.
                ProfileEvent.LoggedOut -> Toast.makeText(context, R.string.profile_logged_out, Toast.LENGTH_SHORT).show()
                ProfileEvent.AccountDeleted -> Toast.makeText(context, R.string.profile_account_deleted, Toast.LENGTH_LONG).show()
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        ProfileScreen(
            state = state,
            onShowDialog = viewModel::showDialog,
            onLogOut = viewModel::logOut,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    ProfileDialogs(state, viewModel)
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onShowDialog: (ProfileDialog) -> Unit,
    onLogOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() },
        )
        AccountHeader(state)

        SectionTitle(stringResource(R.string.profile_preferences))
        OutlinedCard {
            SettingsRow(
                title = stringResource(R.string.profile_appearance),
                value = stringResource(state.themeMode.labelRes()),
                onClick = { onShowDialog(ProfileDialog.Appearance) },
            )
        }

        SectionTitle(stringResource(R.string.profile_data_privacy))
        OutlinedCard {
            Text(
                stringResource(R.string.profile_privacy_statement),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
            SettingsRow(stringResource(R.string.profile_privacy_notice), onClick = { onShowDialog(ProfileDialog.PrivacyNotice) })
            SettingsRow(
                title = stringResource(if (state.exporting) R.string.profile_exporting else R.string.profile_export),
                onClick = { onShowDialog(ProfileDialog.ConfirmExport) },
                enabled = !state.exporting,
                busy = state.exporting,
            )
            SettingsRow(
                title = stringResource(R.string.profile_delete),
                onClick = { onShowDialog(ProfileDialog.DeleteAccount()) },
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.pendingCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    pluralStringResource(R.plurals.profile_pending_note, state.pendingCount, state.pendingCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedButton(
            onClick = onLogOut,
            enabled = !state.loggingOut,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 4.dp),
        ) {
            if (state.loggingOut) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            }
            Text(
                stringResource(if (state.loggingOut) R.string.profile_logging_out else R.string.profile_log_out),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun AccountHeader(state: ProfileUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val avatarDescription = stringResource(R.string.profile_avatar_description)
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MenosanTheme.colors.calm, CircleShape)
                .clearAndSetSemantics { contentDescription = avatarDescription },
            contentAlignment = Alignment.Center,
        ) {
            if (state.initials.isNotEmpty()) {
                Text(
                    state.initials,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MenosanTheme.colors.onCalm,
                )
            } else {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = MenosanTheme.colors.onCalm, modifier = Modifier.size(48.dp))
            }
        }
        Text(
            state.displayName ?: stringResource(R.string.profile_name_fallback),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        state.email?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Pill(
            text = stringResource(R.string.profile_google_account),
            background = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Outlined.Link,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun OutlinedCard(content: @Composable () -> Unit) {
    Surface(
        color = MenosanTheme.colors.card,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

/** A tappable row with a trailing chevron (or a spinner while [busy]). */
@Composable
private fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = color)
        }
    }
}

@Composable
private fun ProfileDialogs(state: ProfileUiState, viewModel: ProfileViewModel) {
    when (val dialog = state.dialog) {
        ProfileDialog.Appearance -> AppearanceSheet(state.themeMode, viewModel::setThemeMode, viewModel::dismissDialog)
        ProfileDialog.PrivacyNotice -> PrivacySheet(viewModel::dismissDialog)
        ProfileDialog.ConfirmExport -> ConfirmDialog(
            icon = Icons.Outlined.FileUpload,
            title = stringResource(R.string.profile_export_title),
            body = stringResource(R.string.profile_export_body),
            confirm = stringResource(R.string.profile_export_confirm),
            onConfirm = viewModel::confirmExport,
            onDismiss = viewModel::dismissDialog,
        )
        is ProfileDialog.LogoutWarning -> LogoutWarningDialog(dialog.pendingCount, viewModel::syncFirst, viewModel::logOutAnyway, viewModel::dismissDialog)
        is ProfileDialog.DeleteAccount -> DeleteAccountDialog(dialog, viewModel::onDeleteTextChange, viewModel::confirmDelete, viewModel::dismissDialog)
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text(
                stringResource(R.string.profile_appearance_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.semantics { heading() },
            )
            Column(Modifier.selectableGroup().padding(vertical = 8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = mode == selected, role = Role.RadioButton, onClick = { onSelect(mode) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Text(stringResource(mode.labelRes()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
            PrimaryButton(stringResource(R.string.profile_done), onDismiss)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrivacyNotice()
            PrimaryButton(stringResource(R.string.profile_done), onDismiss)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Mockup `Profile ExportPopUp`: icon and title, short body, Cancel (outlined) and a filled confirm. */
@Composable
private fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MenosanTheme.colors.card,
        icon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
        title = { Text(title, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            DialogButtons(
                cancel = stringResource(R.string.profile_cancel),
                confirm = confirm,
                onCancel = onDismiss,
                onConfirm = onConfirm,
                confirmColor = MaterialTheme.colorScheme.primaryContainer,
                onConfirmColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    )
}

@Composable
private fun LogoutWarningDialog(pendingCount: Int, onSyncFirst: () -> Unit, onLogOutAnyway: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MenosanTheme.colors.card,
        icon = { Icon(Icons.Outlined.SyncProblem, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(36.dp)) },
        title = { Text(stringResource(R.string.profile_logout_warning_title), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                pluralStringResource(R.plurals.profile_logout_warning_body, pendingCount, pendingCount),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stringResource(R.string.profile_logout_sync_first), onSyncFirst)
                OutlinedButton(
                    onClick = onLogOutAnyway,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.profile_logout_anyway))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.profile_cancel))
                }
            }
        },
    )
}

/** Mockup `Profile DeletePopUp` styling, with the plan's type-DELETE gate instead of a one-tap Proceed. */
@Composable
private fun DeleteAccountDialog(
    dialog: ProfileDialog.DeleteAccount,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MenosanTheme.colors.card,
        icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp)) },
        title = {
            Text(
                stringResource(R.string.profile_delete_title),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.profile_delete_body))
                OutlinedTextField(
                    value = dialog.typed,
                    onValueChange = onTextChange,
                    enabled = !dialog.deleting,
                    singleLine = true,
                    label = { Text(stringResource(R.string.profile_delete_type_label)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                dialog.error?.let { error ->
                    Text(
                        stringResource(
                            when (error) {
                                DeleteError.OFFLINE -> R.string.profile_delete_offline
                                DeleteError.SERVER -> R.string.profile_delete_server
                                DeleteError.SIGN_IN_EXPIRED -> R.string.profile_delete_sign_in
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            DialogButtons(
                cancel = stringResource(R.string.profile_cancel),
                confirm = stringResource(if (dialog.deleting) R.string.profile_deleting else R.string.profile_delete_confirm),
                onCancel = onDismiss,
                onConfirm = onConfirm,
                confirmColor = MaterialTheme.colorScheme.error,
                onConfirmColor = MaterialTheme.colorScheme.onError,
                confirmEnabled = dialog.canDelete,
                cancelEnabled = !dialog.deleting,
            )
        },
    )
}

@Composable
private fun DialogButtons(
    cancel: String,
    confirm: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmColor: Color,
    onConfirmColor: Color,
    confirmEnabled: Boolean = true,
    cancelEnabled: Boolean = true,
) {
    // Side by side as in the mockups. The labels are short, so they fit at large font scales too.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onCancel,
            enabled = cancelEnabled,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Text(cancel, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = confirmColor, contentColor = onConfirmColor),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Text(confirm, textAlign = TextAlign.Center)
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.profile_theme_system
    ThemeMode.LIGHT -> R.string.profile_theme_light
    ThemeMode.DARK -> R.string.profile_theme_dark
}

private const val EXPORT_MIME_TYPE = "application/json"

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    MenosanTheme {
        ProfileScreen(
            state = ProfileUiState(displayName = "Liza Cabaraban", email = "liza@example.com", pendingCount = 2),
            onShowDialog = {},
            onLogOut = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF171C19)
@Composable
private fun ProfileScreenDarkPreview() {
    MenosanTheme(darkTheme = true) {
        ProfileScreen(
            state = ProfileUiState(displayName = "Liza Cabaraban", email = "liza@example.com"),
            onShowDialog = {},
            onLogOut = {},
        )
    }
}

