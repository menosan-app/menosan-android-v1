package app.menosan.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.menosan.android.R
import app.menosan.android.core.auth.AuthUser
import app.menosan.android.core.ui.components.PlaceholderScreen
import app.menosan.android.feature.auth.CreateAccountScreen
import app.menosan.android.feature.auth.SignInScreen
import app.menosan.android.feature.dashboard.DashboardScreen

/**
 * The app's navigation graph. Workstreams replace their [PlaceholderScreen] with the real screen.
 * When the user signs out (from anywhere), the back stack is cleared and sign-in is shown.
 */
@Composable
fun MenosanNavHost(
    startDestination: Any,
    authUser: AuthUser?,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(authUser) {
        val current = navController.currentDestination ?: return@LaunchedEffect
        if (authUser == null && !current.hasRoute<SignInRoute>()) {
            navController.navigate(SignInRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<SignInRoute> {
            SignInScreen(
                onSignedIn = { navController.navigateClearingStack(DashboardRoute) },
                onNeedsAccount = { navController.navigateClearingStack(CreateAccountRoute) },
            )
        }
        composable<CreateAccountRoute> {
            CreateAccountScreen(onCreated = { navController.navigateClearingStack(DashboardRoute) })
        }
        composable<DashboardRoute> {
            DashboardScreen(
                onLogManually = { navController.navigate(LogManualRoute()) },
                onLogWithPhoto = { navController.navigate(LogPhotoRoute) },
                onEntries = { navController.navigate(EntriesRoute) },
                onReports = { navController.navigate(HistoryRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<LogManualRoute> {
            PlaceholderScreen(stringResource(R.string.dashboard_log_manually), "AN-1") { navController.popBackStack() }
        }
        composable<LogPhotoRoute> {
            PlaceholderScreen(stringResource(R.string.dashboard_log_photo), "AN-2") { navController.popBackStack() }
        }
        composable<EntriesRoute> {
            PlaceholderScreen(stringResource(R.string.nav_entries), "AN-1") { navController.popBackStack() }
        }
        composable<HistoryRoute> {
            PlaceholderScreen(stringResource(R.string.nav_reports), "AN-3") { navController.popBackStack() }
        }
        composable<ReportRoute> {
            PlaceholderScreen(stringResource(R.string.nav_report), "AN-3") { navController.popBackStack() }
        }
        composable<SettingsRoute> {
            PlaceholderScreen(stringResource(R.string.nav_settings), "AN-4") { navController.popBackStack() }
        }
    }
}

private fun NavHostController.navigateClearingStack(route: Any) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
