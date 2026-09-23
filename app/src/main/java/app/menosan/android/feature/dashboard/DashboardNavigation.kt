package app.menosan.android.feature.dashboard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.navigation.DashboardRoute

/**
 * AN-4 owns this file: register Home here. Navigate to other features with their routes from
 * `navigation/Routes.kt` (e.g. `navController.navigate(ReportRoute(weekStart))`).
 */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.dashboardScreens(navController: NavHostController) {
    composable<DashboardRoute> { DashboardScreen() }
}
