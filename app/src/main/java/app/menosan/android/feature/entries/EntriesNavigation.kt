package app.menosan.android.feature.entries

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.R
import app.menosan.android.core.ui.components.PlaceholderScreen
import app.menosan.android.navigation.EntriesRoute

/**
 * AN-1 owns this file: register this feature's screens here (and any feature-only routes, declared in this package).
 * `MenosanNavHost` just calls [entriesScreens], so parallel workstreams never edit the same navigation file.
 */
fun NavGraphBuilder.entriesScreens(navController: NavHostController) {
    composable<EntriesRoute> {
        PlaceholderScreen(stringResource(R.string.tab_audit), "AN-1", onBack = null)
    }
}
