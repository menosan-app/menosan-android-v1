package app.menosan.android.feature.dashboard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.feature.entries.EntryDetailsRoute
import app.menosan.android.navigation.DashboardRoute
import app.menosan.android.navigation.EntriesRoute
import app.menosan.android.navigation.LogManualRoute
import app.menosan.android.navigation.LogPhotoRoute
import app.menosan.android.navigation.ReportRoute

/**
 * AN-4 owns this file: register Home here. Navigate to other features with their routes from
 * `navigation/Routes.kt` (e.g. `navController.navigate(ReportRoute(weekStart))`).
 */
fun NavGraphBuilder.dashboardScreens(navController: NavHostController) {
    composable<DashboardRoute> {
        HomeRoute(
            actions = HomeActions(
                onLogManually = { navController.navigate(LogManualRoute()) },
                onLogWithPhoto = { navController.navigate(LogPhotoRoute) },
                onOpenReport = { navController.navigate(ReportRoute(it.toString())) },
                onViewAllEntries = {
                    // Same as tapping the Audit tab (MenosanNavHost.navigateToTab), so the bottom bar stays in sync.
                    navController.navigate(EntriesRoute) {
                        popUpTo<DashboardRoute> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenEntry = { navController.navigate(EntryDetailsRoute(it)) },
                onEditEntry = { navController.navigate(LogManualRoute(entryId = it)) },
            ),
        )
    }
}
