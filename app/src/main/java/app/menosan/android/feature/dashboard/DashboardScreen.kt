package app.menosan.android.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.menosan.android.R
import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.network.ConnectivityObserver
import app.menosan.android.core.time.WeekCalc
import app.menosan.android.core.ui.components.MenosanWordmark
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.feature.auth.SignOutAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// AN-0 placeholder. AN-4 owns the real Home (mockup `Home`, plan §10): weekly bars, latest report and hotspot,
// recommendations, active adoptions, recent entries, and the pending-sync badge.

data class DashboardUiState(
    val displayName: String? = null,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val online: Boolean = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    auth: AuthService,
    clock: Clock,
    connectivity: ConnectivityObserver,
    private val signOutAction: SignOutAction,
) : ViewModel() {
    private val initial = WeekCalc.currentWeekStart(clock).let { start ->
        DashboardUiState(
            displayName = auth.currentUser?.displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() },
            weekStart = start,
            weekEnd = WeekCalc.weekEnd(start),
            online = connectivity.isOnline(),
        )
    }

    val state: StateFlow<DashboardUiState> = connectivity.online
        .map { initial.copy(online = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** Temporary until AN-4's logout (which must check the outbox and clear Room first). */
    fun signOut() {
        viewModelScope.launch { signOutAction() }
    }
}

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardContent(state = state, onSignOut = viewModel::signOut)
}

@Composable
fun DashboardContent(state: DashboardUiState, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MenosanWordmark()
            Spacer(Modifier.weight(1f))
            ConnectionPill(online = state.online)
        }
        Column {
            Text(
                text = state.displayName?.let { stringResource(R.string.dashboard_greeting_name, it) }
                    ?: stringResource(R.string.dashboard_greeting),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val onHero = MaterialTheme.colorScheme.onPrimaryContainer
            Text(stringResource(R.string.dashboard_this_week), style = MaterialTheme.typography.titleMedium, color = onHero)
            Text(formatWeekRange(state.weekStart, state.weekEnd), style = MaterialTheme.typography.headlineMedium, color = onHero)
            Text(stringResource(R.string.dashboard_log_hint), style = MaterialTheme.typography.bodyMedium, color = onHero)
        }
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.action_sign_out))
        }
    }
}

/** The "Online" / "Offline" pill of the Home header. */
@Composable
private fun ConnectionPill(online: Boolean) {
    val color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = stringResource(if (online) R.string.status_online else R.string.status_offline),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .border(BorderStroke(1.5.dp, color), MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/** "Sep 27 – Oct 3". */
fun formatWeekRange(start: LocalDate, end: LocalDate): String = "${DAY_FORMAT.format(start)} – ${DAY_FORMAT.format(end)}"

@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    MenosanTheme {
        DashboardContent(
            state = DashboardUiState("Liza", LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 3)),
            onSignOut = {},
        )
    }
}
