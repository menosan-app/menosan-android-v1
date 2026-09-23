package app.menosan.android.feature.reports

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.R
import app.menosan.android.core.ui.components.PlaceholderScreen
import app.menosan.android.navigation.HistoryRoute
import app.menosan.android.navigation.ReportRoute

/**
 * AN-3 owns this file: register this feature's screens here (and any feature-only routes, declared in this package).
 * `MenosanNavHost` just calls [reportsScreens], so parallel workstreams never edit the same navigation file.
 */
fun NavGraphBuilder.reportsScreens(navController: NavHostController) {
    composable<HistoryRoute> {
        PlaceholderScreen(stringResource(R.string.tab_insights), "AN-3", onBack = null)
    }
    composable<ReportRoute> {
        PlaceholderScreen(stringResource(R.string.nav_report), "AN-3") { navController.popBackStack() }
    }
}
