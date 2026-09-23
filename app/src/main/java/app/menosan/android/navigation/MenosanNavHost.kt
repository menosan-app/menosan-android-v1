package app.menosan.android.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.menosan.android.R
import app.menosan.android.core.auth.AuthUser
import app.menosan.android.core.ui.components.PlaceholderScreen
import app.menosan.android.feature.auth.AccountReadyScreen
import app.menosan.android.feature.auth.CreateAccountScreen
import app.menosan.android.feature.auth.SignInScreen
import app.menosan.android.feature.auth.WelcomeScreen
import app.menosan.android.feature.dashboard.DashboardScreen

/**
 * The app's navigation graph. The four tabs (Home, Audit, Insights, Profile) show the bottom bar with the "+" button.
 * Workstreams replace their [PlaceholderScreen] with the real screen. Signing out anywhere returns to Welcome.
 */
@Composable
fun MenosanNavHost(
    startDestination: Any,
    authUser: AuthUser?,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(authUser) {
        val current = navController.currentDestination ?: return@LaunchedEffect
        val signedOutScreen = current.hasRoute<WelcomeRoute>() || current.hasRoute<SignInRoute>() ||
            current.hasRoute<CreateAccountRoute>()
        if (authUser == null && !signedOutScreen) navController.navigateClearingStack(WelcomeRoute)
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = TopLevelTab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hasRoute(tab.route::class) == true
    }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentTab != null) {
                MainBottomBar(
                    selected = currentTab,
                    onSelect = { navController.navigateToTab(it) },
                    onAdd = { showAddSheet = true },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(padding),
        ) {
            // Signed out
            composable<WelcomeRoute> {
                WelcomeScreen(
                    onCreateAccount = { navController.navigate(CreateAccountRoute) },
                    onLogIn = { navController.navigate(SignInRoute) },
                )
            }
            composable<SignInRoute> {
                SignInScreen(
                    onBack = navController.backOrNull(),
                    onSignedIn = { navController.navigateClearingStack(DashboardRoute) },
                    onNeedsAccount = { navController.navigateClearingStack(CreateAccountRoute) },
                    onCreateAccount = {
                        navController.navigate(CreateAccountRoute) { popUpTo<SignInRoute> { inclusive = true } }
                    },
                )
            }
            composable<CreateAccountRoute> {
                CreateAccountScreen(
                    onBack = navController.backOrNull(),
                    onCreated = { navController.navigateClearingStack(AccountReadyRoute) },
                    onAlreadyHadAccount = { navController.navigateClearingStack(DashboardRoute) },
                    onLogIn = { navController.navigate(SignInRoute) { popUpTo<CreateAccountRoute> { inclusive = true } } },
                )
            }
            composable<AccountReadyRoute> {
                AccountReadyScreen(onGetStarted = { navController.navigateClearingStack(DashboardRoute) })
            }

            // Tabs
            composable<DashboardRoute> { DashboardScreen() }
            composable<EntriesRoute> {
                PlaceholderScreen(stringResource(R.string.tab_audit), "AN-1", onBack = null)
            }
            composable<HistoryRoute> {
                PlaceholderScreen(stringResource(R.string.tab_insights), "AN-3", onBack = null)
            }
            composable<SettingsRoute> {
                PlaceholderScreen(stringResource(R.string.tab_profile), "AN-4", onBack = null)
            }

            // Sub-screens
            composable<LogManualRoute> {
                PlaceholderScreen(stringResource(R.string.add_entry_manual), "AN-1") { navController.popBackStack() }
            }
            composable<LogPhotoRoute> {
                PlaceholderScreen(stringResource(R.string.add_entry_photo), "AN-2") { navController.popBackStack() }
            }
            composable<ReportRoute> {
                PlaceholderScreen(stringResource(R.string.nav_report), "AN-3") { navController.popBackStack() }
            }
        }
    }

    if (showAddSheet) {
        AddEntrySheet(
            onDismiss = { showAddSheet = false },
            onScanWithPhoto = {
                showAddSheet = false
                navController.navigate(LogPhotoRoute)
            },
            onLogManually = {
                showAddSheet = false
                navController.navigate(LogManualRoute())
            },
        )
    }
}

/** A back action only when there is somewhere to go back to (e.g. Log in opened from Welcome). */
private fun NavHostController.backOrNull(): (() -> Unit)? =
    if (previousBackStackEntry != null) ({ popBackStack() }) else null

private fun NavHostController.navigateToTab(tab: TopLevelTab) = navigate(tab.route) {
    // Home is always the root of the signed-in back stack.
    popUpTo<DashboardRoute> { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavHostController.navigateClearingStack(route: Any) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
