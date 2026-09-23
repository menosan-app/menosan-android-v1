package app.menosan.android.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.menosan.android.R
import app.menosan.android.core.ui.theme.MenosanTheme

/** The four tabs of the bottom bar (mockups: Home · Audit · + · Insights · Profile). */
enum class TopLevelTab(
    val route: Any,
    val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home(DashboardRoute, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    Audit(EntriesRoute, R.string.tab_audit, Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    Insights(HistoryRoute, R.string.tab_insights, Icons.Outlined.Insights, Icons.Filled.Insights),
    Profile(SettingsRoute, R.string.tab_profile, Icons.Outlined.Person, Icons.Filled.Person),
}

/** Bottom navigation with the raised "+" button in the middle that opens the "Add New Entry" sheet. */
@Composable
fun MainBottomBar(
    selected: TopLevelTab?,
    onSelect: (TopLevelTab) -> Unit,
    onAdd: () -> Unit,
) {
    Box(contentAlignment = Alignment.TopCenter) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                TopLevelTab.entries.forEachIndexed { index, tab ->
                    if (index == 2) Spacer(Modifier.weight(1f)) // Room for the "+" button.
                    val isSelected = tab == selected
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            modifier = Modifier
                .offset(y = (-28).dp)
                .size(64.dp)
                .border(BorderStroke(4.dp, MaterialTheme.colorScheme.background), CircleShape),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_entry_title), modifier = Modifier.size(32.dp))
        }
    }
}

/** "Add New Entry" (mockup `WasteLog PopUp`): scan with photo or log manually. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntrySheet(
    onDismiss: () -> Unit,
    onScanWithPhoto: () -> Unit,
    onLogManually: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.add_entry_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.add_entry_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            AddEntryOption(Icons.Filled.PhotoLibrary, R.string.add_entry_photo, R.string.add_entry_photo_body, onScanWithPhoto)
            AddEntryOption(Icons.Filled.EditNote, R.string.add_entry_manual, R.string.add_entry_manual_body, onLogManually)
        }
    }
}

@Composable
private fun AddEntryOption(icon: ImageVector, title: Int, body: Int, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MenosanTheme.colors.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
