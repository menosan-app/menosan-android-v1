package app.menosan.android.feature.logging

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.R
import app.menosan.android.core.ui.components.PlaceholderScreen
import app.menosan.android.navigation.LogManualRoute

/**
 * AN-1 owns this file: register this feature's screens here (and any feature-only routes, declared in this package).
 * `MenosanNavHost` just calls [loggingScreens], so parallel workstreams never edit the same navigation file.
 */
fun NavGraphBuilder.loggingScreens(navController: NavHostController) {
    composable<LogManualRoute> {
        PlaceholderScreen(stringResource(R.string.add_entry_manual), "AN-1") { navController.popBackStack() }
    }
}
