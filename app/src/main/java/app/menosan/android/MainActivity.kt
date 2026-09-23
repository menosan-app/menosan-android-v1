package app.menosan.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.ui.theme.MenosanTheme
import app.menosan.android.data.repo.AccountRepository
import app.menosan.android.navigation.DashboardRoute
import app.menosan.android.navigation.MenosanNavHost
import app.menosan.android.navigation.SignInRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: AuthService

    @Inject lateinit var accounts: AccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A confirmed account opens straight to the dashboard, even offline (manual logging works offline).
        // Otherwise sign-in runs the Google + `GET /v1/me` checks.
        val startDestination: Any = if (accounts.hasConfirmedAccount()) DashboardRoute else SignInRoute

        setContent {
            val authUser by auth.authState.collectAsStateWithLifecycle(initialValue = auth.currentUser)
            MenosanTheme {
                MenosanNavHost(startDestination = remember { startDestination }, authUser = authUser)
            }
        }
    }
}
