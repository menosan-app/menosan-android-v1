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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.menosan.android.core.auth.AuthUser
import app.menosan.android.feature.auth.AccountReadyScreen
import app.menosan.android.feature.auth.CreateAccountScreen
import app.menosan.android.feature.auth.SignInScreen
import app.menosan.android.feature.auth.WelcomeScreen
import app.menosan.android.feature.dashboard.dashboardScreens
import app.menosan.android.feature.entries.entriesScreens
import app.menosan.android.feature.logging.loggingScreens
import app.menosan.android.feature.photo.photoScreens
import app.menosan.android.feature.reports.reportsScreens
import app.menosan.android.feature.settings.settingsScreens

/**
 * The app's navigation graph. The four tabs (Home, Audit, Insights, Profile) show the bottom bar with the "+" button.
 * Features register their screens through `NavGraphBuilder.xxxScreens()` in their own package. Signing out anywhere returns to Welcome.
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

            // Each feature registers its own screens (one file per workstream, so parallel work doesn't collide).
            dashboardScreens(navController) // AN-4
            entriesScreens(navController) // AN-1
            loggingScreens(navController) // AN-1
            photoScreens(navController) // AN-2
            reportsScreens(navController) // AN-3
            settingsScreens(navController) // AN-4
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
