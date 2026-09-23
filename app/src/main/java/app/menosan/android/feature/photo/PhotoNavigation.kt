package app.menosan.android.feature.photo

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.navigation.LogManualRoute
import app.menosan.android.navigation.LogPhotoRoute

/**
 * AN-2 owns this file: register this feature's screens here (and any feature-only routes, declared in this package).
 * `MenosanNavHost` just calls [photoScreens], so parallel workstreams never edit the same navigation file.
 */
fun NavGraphBuilder.photoScreens(navController: NavHostController) {
    composable<LogPhotoRoute> {
        PhotoLogRoute(
            onDone = { navController.popBackStack() },
            // Swap to the manual form, so its back button returns to where the user started (Home or Audit).
            onLogManually = {
                navController.navigate(LogManualRoute()) { popUpTo<LogPhotoRoute> { inclusive = true } }
            },
        )
    }
}
