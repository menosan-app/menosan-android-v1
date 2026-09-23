package app.menosan.android.feature.settings

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.R
import app.menosan.android.core.ui.components.PlaceholderScreen
import app.menosan.android.navigation.SettingsRoute

/**
 * AN-4 owns this file: register this feature's screens here (and any feature-only routes, declared in this package).
 * `MenosanNavHost` just calls [settingsScreens], so parallel workstreams never edit the same navigation file.
 */
fun NavGraphBuilder.settingsScreens(navController: NavHostController) {
    composable<SettingsRoute> {
        PlaceholderScreen(stringResource(R.string.tab_profile), "AN-4", onBack = null)
    }
}
