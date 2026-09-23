package app.menosan.android.feature.reports

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.navigation.HistoryRoute
import app.menosan.android.navigation.ReportRoute

/**
 * AN-3 owns this file: register this feature's screens here (and any feature-only routes, declared in this package).
 * `MenosanNavHost` just calls [reportsScreens], so parallel workstreams never edit the same navigation file.
 */
fun NavGraphBuilder.reportsScreens(navController: NavHostController) {
    composable<HistoryRoute> {
        InsightsScreen(onOpenReport = { week -> navController.navigate(ReportRoute(week.toString())) })
    }
    composable<ReportRoute> {
        ReportScreen(onBack = { navController.popBackStack() })
    }
}
