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
import app.menosan.android.navigation.WelcomeRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: AuthService

    @Inject lateinit var accounts: AccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A confirmed account opens straight to Home, even offline (manual logging works offline). A Google user
        // without a confirmed account resumes at Log in, which re-checks `GET /v1/me`. Everyone else sees Welcome.
        val startDestination: Any = when {
            accounts.hasConfirmedAccount() -> DashboardRoute
            auth.currentUser != null -> SignInRoute
            else -> WelcomeRoute
        }

        setContent {
            val authUser by auth.authState.collectAsStateWithLifecycle(initialValue = auth.currentUser)
            MenosanTheme {
                MenosanNavHost(startDestination = remember { startDestination }, authUser = authUser)
            }
        }
    }
}
