package app.menosan.android.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.navigation.SettingsRoute

/**
 * AN-4 owns this file: register this feature's screens here (and any feature-only routes, declared in this package).
 * `MenosanNavHost` just calls [settingsScreens], so parallel workstreams never edit the same navigation file.
 * Logout and account deletion only sign out: the nav host returns to Welcome when the auth state becomes null.
 */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.settingsScreens(navController: NavHostController) {
    composable<SettingsRoute> { ProfileRoute() }
}
