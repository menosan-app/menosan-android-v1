package app.menosan.android.feature.photo

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.menosan.android.R
import app.menosan.android.core.ui.components.BrandLoading
import app.menosan.android.core.ui.components.MessageBanner
import app.menosan.android.core.ui.components.ScreenHeader
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.feature.logging.EntryField
import app.menosan.android.feature.logging.EntryFormFields
import app.menosan.android.feature.logging.EntryFormState

/**
 * Photo logging (plan §10, AN-2). [onDone] leaves the flow (back, or after a save); [onLogManually] swaps to the
 * manual form.
 */
@Composable
fun PhotoLogRoute(
    onDone: () -> Unit,
    onLogManually: () -> Unit,
    viewModel: PhotoLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        viewModel.onCaptureResult(saved)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onGalleryResult(uri?.toString())
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                // Like manual logging: a toast survives the navigation back to wherever the user came from.
                is PhotoEvent.Saved -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    onDone()
                }
            }
        }
    }

    // Back from loading, an error, or the review returns to taking or choosing a photo.
    BackHandler(enabled = state.step !is PhotoStep.Pick) { viewModel.backToPick() }

    val takePhoto: () -> Unit = {
        val uri = viewModel.startCapture()
        if (uri != null) {
            try {
                takePicture.launch(Uri.parse(uri))
            } catch (_: ActivityNotFoundException) {
                viewModel.onCameraUnavailable()
            }
        }
    }
    val choosePhoto: () -> Unit = {
        try {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            viewModel.onGalleryResult(null)
        }
    }

    when (val step = state.step) {
        PhotoStep.Pick -> PhotoPickScreen(
            online = state.online,
            onBack = onDone,
            onTakePhoto = takePhoto,
            onChoosePhoto = choosePhoto,
            onLogManually = onLogManually,
        )
        is PhotoStep.Analyzing -> PhotoAnalyzingScreen(wakingUp = step.wakingUp, onCancel = viewModel::backToPick)
        is PhotoStep.Failed -> PhotoErrorScreen(
            error = step.error,
            canRetry = step.error.kind.canRetrySame && state.canRetrySamePhoto,
            onBack = viewModel::backToPick,
            onRetry = viewModel::retry,
            onAnotherPhoto = viewModel::backToPick,
            onLogManually = onLogManually,
        )
        is PhotoStep.Review -> PhotoReviewScreen(
            step = step,
            onBack = viewModel::backToPick,
            onFormChange = viewModel::onFormChange,
            onConfirmField = viewModel::onConfirmField,
            onConfirmedChange = viewModel::onConfirmedChange,
            onSave = viewModel::save,
            onAnotherPhoto = viewModel::backToPick,
        )
    }
}

/** Mockup `Upload Photo`: choose from the gallery or take a photo with the system camera. Online only. */
@Composable
fun PhotoPickScreen(
    online: Boolean,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onLogManually: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.photo_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!online) {
                MessageBanner(
                    title = stringResource(R.string.photo_offline_title),
                    text = stringResource(R.string.photo_offline_body),
                    icon = Icons.Outlined.CloudOff,
                )
                PrimaryButton(stringResource(R.string.photo_log_manually), onClick = onLogManually)
            }
            UploadCard(enabled = online, onChoosePhoto = onChoosePhoto, onTakePhoto = onTakePhoto)
            MessageBanner(
                title = stringResource(R.string.photo_ai_notice_title),
                text = stringResource(R.string.photo_ai_notice_body),
                icon = Icons.Outlined.AutoAwesome,
            )
            MessageBanner(text = stringResource(R.string.photo_tip), icon = Icons.Outlined.Lightbulb)
        }
        if (online) {
            TextButton(
                onClick = onLogManually,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                Text(stringResource(R.string.photo_log_manually_instead), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun UploadCard(enabled: Boolean, onChoosePhoto: () -> Unit, onTakePhoto: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val alpha = if (enabled) 1f else 0.45f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MenosanTheme.colors.card, MaterialTheme.shapes.medium)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClick = onChoosePhoto)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.size(88.dp),
            )
            Text(
                stringResource(R.string.photo_upload_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                stringResource(R.string.photo_upload_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                textAlign = TextAlign.Center,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        PrimaryButton(
            text = stringResource(R.string.photo_take),
            onClick = onTakePhoto,
            enabled = enabled,
            icon = Icons.Outlined.PhotoCamera,
        )
    }
}

/** Mockup `Loading Screen (After Scanning)`, plus a patient hint when the server is waking up. */
@Composable
fun PhotoAnalyzingScreen(wakingUp: Boolean, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BrandLoading(
            title = stringResource(R.string.photo_analyzing_title),
            subtitle = stringResource(if (wakingUp) R.string.photo_analyzing_waking else R.string.photo_analyzing_body),
        )
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.photo_cancel), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun PhotoErrorScreen(
    error: PhotoError,
    canRetry: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAnotherPhoto: () -> Unit,
    onLogManually: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.photo_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .background(MenosanTheme.colors.calm, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(error.kind.icon(), contentDescription = null, tint = MenosanTheme.colors.onCalm, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(error.kind.title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            val resetsAt = error.resetsAt
            Text(
                if (error.kind == PhotoErrorKind.RATE_LIMITED && resetsAt != null) {
                    stringResource(R.string.photo_error_rate_limited_body_at, PhotoFormats.resetTime(resetsAt))
                } else {
                    stringResource(error.kind.body)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                canRetry -> {
                    PrimaryButton(stringResource(R.string.photo_try_again), onClick = onRetry)
                    SecondaryButton(stringResource(R.string.photo_try_another), onClick = onAnotherPhoto)
                    SecondaryButton(stringResource(R.string.photo_log_manually), onClick = onLogManually)
                }
                error.kind == PhotoErrorKind.RATE_LIMITED -> {
                    PrimaryButton(stringResource(R.string.photo_log_manually), onClick = onLogManually)
                    SecondaryButton(stringResource(R.string.photo_back), onClick = onBack)
                }
                else -> {
                    PrimaryButton(stringResource(R.string.photo_try_another), onClick = onAnotherPhoto)
                    SecondaryButton(stringResource(R.string.photo_log_manually), onClick = onLogManually)
                }
            }
        }
    }
}

/**
 * The review form: the shared entry form prefilled from the suggestion, with AI-suggested fields tinted and chipped
 * until edited or confirmed (NFR10), the server's warning (SFR9.5), and the required confirmation (SFR9.2, SFR9.4).
 */
@Composable
fun PhotoReviewScreen(
    step: PhotoStep.Review,
    onBack: () -> Unit,
    onFormChange: (EntryFormState) -> Unit,
    onConfirmField: (EntryField) -> Unit,
    onConfirmedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onAnotherPhoto: () -> Unit,
) {
    val review = step.review
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        ScreenHeader(title = stringResource(R.string.photo_review_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MessageBanner(
                title = stringResource(R.string.photo_review_warning_title),
                text = review.warning,
                icon = Icons.Outlined.AutoAwesome,
            )
            if (review.aiSuggested.isNotEmpty()) {
                Text(
                    stringResource(R.string.photo_review_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EntryFormFields(
                state = review.form,
                taxonomy = step.taxonomy,
                onChange = onFormChange,
                showErrors = step.showErrors,
                aiSuggested = review.aiSuggested,
                onConfirmField = onConfirmField,
                enabled = !step.saving,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MenosanTheme.colors.mist, MaterialTheme.shapes.small)
                    .toggleable(
                        value = review.confirmed,
                        enabled = !step.saving,
                        role = Role.Checkbox,
                        onValueChange = onConfirmedChange,
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Checkbox(checked = review.confirmed, onCheckedChange = null, enabled = !step.saving)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.photo_review_confirm), style = MaterialTheme.typography.titleSmall)
            }
            step.saveError?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onSave,
                enabled = step.canSave,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (step.saving) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        stringResource(R.string.photo_review_save),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
            TextButton(
                onClick = onAnotherPhoto,
                enabled = !step.saving,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.photo_try_another), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        // Brand fill: Moss in both modes (DESIGN.md §2).
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
    }
}

private fun PhotoErrorKind.icon(): ImageVector = when (this) {
    PhotoErrorKind.NETWORK -> Icons.Outlined.WifiOff
    PhotoErrorKind.NOT_WASTE -> Icons.Outlined.ImageSearch
    PhotoErrorKind.RATE_LIMITED -> Icons.Outlined.Schedule
    PhotoErrorKind.NO_CAMERA -> Icons.Outlined.PhotoCamera
    PhotoErrorKind.UNREADABLE, PhotoErrorKind.IMAGE_TOO_LARGE, PhotoErrorKind.INVALID_IMAGE -> Icons.Outlined.Image
    PhotoErrorKind.ANALYSIS_FAILED, PhotoErrorKind.SIGNED_OUT, PhotoErrorKind.UNKNOWN -> Icons.Outlined.WarningAmber
}
