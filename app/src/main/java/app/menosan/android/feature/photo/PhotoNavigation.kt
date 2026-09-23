package app.menosan.android.feature.photo

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.R
import app.menosan.android.core.ui.components.PlaceholderScreen
import app.menosan.android.navigation.LogPhotoRoute

/**
 * AN-2 owns this file: register this feature's screens here (and any feature-only routes, declared in this package).
 * `MenosanNavHost` just calls [photoScreens], so parallel workstreams never edit the same navigation file.
 */
fun NavGraphBuilder.photoScreens(navController: NavHostController) {
    composable<LogPhotoRoute> {
        PlaceholderScreen(stringResource(R.string.add_entry_photo), "AN-2") { navController.popBackStack() }
    }
}
