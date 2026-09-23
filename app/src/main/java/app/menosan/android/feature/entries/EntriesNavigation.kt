package app.menosan.android.feature.entries

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.menosan.android.R
import app.menosan.android.navigation.EntriesRoute
import app.menosan.android.navigation.LogManualRoute
import app.menosan.android.navigation.LogPhotoRoute

/**
 * AN-1 owns this file: register this feature's screens here (and any feature-only routes, declared in this package).
 * `MenosanNavHost` just calls [entriesScreens], so parallel workstreams never edit the same navigation file.
 */
fun NavGraphBuilder.entriesScreens(navController: NavHostController) {
    composable<EntriesRoute> {
        AuditRoute(
            onLogManually = { navController.navigate(LogManualRoute()) },
            onScanWithPhoto = { navController.navigate(LogPhotoRoute) },
            onOpenEntry = { navController.navigate(EntryDetailsRoute(it)) },
            onEditEntry = { navController.navigate(LogManualRoute(entryId = it)) },
        )
    }
    composable<EntryDetailsRoute> {
        val context = LocalContext.current
        EntryDetailsRouteScreen(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(LogManualRoute(entryId = it)) },
            onDeleted = {
                Toast.makeText(context, R.string.delete_done, Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            },
        )
    }
}
