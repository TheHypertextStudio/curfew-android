package studio.hypertext.curfew

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import studio.hypertext.curfew.account.AccountViewModel
import studio.hypertext.curfew.platform.SystemSettingsLauncher
import studio.hypertext.curfew.ui.CurfewRoot
import studio.hypertext.curfew.ui.RingingAlarmViewModel

/**
 * Owns Android activity entry and intent delivery. It translates each Intent at the boundary,
 * sends account and alarm commands to their owners, and exposes protection navigation to the
 * composition. OAuth callback data is cleared once consumed so its code is not retained in the
 * Activity.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject internal lateinit var systemSettings: SystemSettingsLauncher

    private val account by viewModels<AccountViewModel>()
    private val ringing by viewModels<RingingAlarmViewModel>()
    private val protectionEntry = MutableStateFlow<LaunchRequest.Enforcement?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dispatchIntent(intent, allowOAuth = savedInstanceState == null)
        setContent {
            CurfewRoot(systemSettings, protectionEntry) { entry -> protectionEntry.compareAndSet(entry, null) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatchIntent(intent)
    }

    private fun dispatchIntent(intent: Intent, allowOAuth: Boolean = true) {
        val request = intent.toLaunchRequest()
        // Consumed callbacks and route requests should not be replayed on recreation.
        setIntent(if (request is LaunchRequest.OAuthCallback || request is LaunchRequest.Enforcement) Intent() else intent)
        when (request) {
            is LaunchRequest.OAuthCallback -> {
                if (allowOAuth) account.completeSignIn(request.url)
            }
            is LaunchRequest.ShowAlarm -> ringing.present(request.campaignId)
            is LaunchRequest.Enforcement -> protectionEntry.value = request
            LaunchRequest.None -> Unit
        }
    }

    companion object {
        const val EXTRA_SHOW_ALARM = "show_alarm"
    }
}
