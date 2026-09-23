package app.menosan.android.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.menosan.android.R
import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.time.WeekCalc
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.feature.auth.SignOutAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// AN-0 placeholder. AN-4 owns the real dashboard (plan §10): pending-sync badge, latest report card, active adoptions.

data class DashboardUiState(
    val displayName: String? = null,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    auth: AuthService,
    clock: Clock,
    private val signOutAction: SignOutAction,
) : ViewModel() {
    private val weekStart = WeekCalc.currentWeekStart(clock)

    private val _state = MutableStateFlow(
        DashboardUiState(
            displayName = auth.currentUser?.displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() },
            weekStart = weekStart,
            weekEnd = WeekCalc.weekEnd(weekStart),
        ),
    )
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    /** Temporary until AN-4's logout (which must check the outbox and clear Room first). */
    fun signOut() {
        viewModelScope.launch { signOutAction() }
    }
}

@Composable
fun DashboardScreen(
    onLogManually: () -> Unit,
    onLogWithPhoto: () -> Unit,
    onEntries: () -> Unit,
    onReports: () -> Unit,
    onSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        onLogManually = onLogManually,
        onLogWithPhoto = onLogWithPhoto,
        onEntries = onEntries,
        onReports = onReports,
        onSettings = onSettings,
        onSignOut = viewModel::signOut,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onLogManually: () -> Unit,
    onLogWithPhoto: () -> Unit,
    onEntries: () -> Unit,
    onReports: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.brand_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.displayName?.let { stringResource(R.string.dashboard_greeting_name, it) }
                    ?: stringResource(R.string.dashboard_greeting),
                style = MaterialTheme.typography.headlineSmall,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.dashboard_this_week), style = MaterialTheme.typography.labelLarge)
                    Text(formatWeekRange(state.weekStart, state.weekEnd), style = MaterialTheme.typography.titleLarge)
                }
            }
            ActionButton(Icons.Filled.EditNote, R.string.dashboard_log_manually, onLogManually, primary = true)
            ActionButton(Icons.Filled.CameraAlt, R.string.dashboard_log_photo, onLogWithPhoto)
            ActionButton(Icons.AutoMirrored.Filled.ListAlt, R.string.nav_entries, onEntries)
            ActionButton(Icons.Filled.Insights, R.string.nav_reports, onReports)
            TextButton(onClick = onSignOut) { Text(stringResource(R.string.action_sign_out)) }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: Int, onClick: () -> Unit, primary: Boolean = false) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
    }
    if (primary) {
        Button(onClick = onClick, modifier = modifier) { content() }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { content() }
    }
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/** "Sep 27 – Oct 3". */
fun formatWeekRange(start: LocalDate, end: LocalDate): String = "${DAY_FORMAT.format(start)} – ${DAY_FORMAT.format(end)}"

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    MenosanTheme {
        DashboardContent(
            state = DashboardUiState("Juan", LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 3)),
            onLogManually = {}, onLogWithPhoto = {}, onEntries = {}, onReports = {}, onSettings = {}, onSignOut = {},
        )
    }
}
