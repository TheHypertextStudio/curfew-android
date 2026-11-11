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
import studio.hypertext.curfew.platform.SystemSettingsLauncher
import studio.hypertext.curfew.ui.CurfewRoot
import studio.hypertext.curfew.ui.CurfewViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject internal lateinit var systemSettings: SystemSettingsLauncher

    private val viewModel: CurfewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        forwardLaunch(intent)
        setContent { CurfewRoot(viewModel, systemSettings) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forwardLaunch(intent)
    }

    private fun forwardLaunch(intent: Intent) {
        val request = intent.toLaunchRequest()
        setIntent(if (request is LaunchRequest.OAuthCallback) Intent() else intent)
        viewModel.onLaunchRequest(request)
    }

    companion object {
        const val EXTRA_SHOW_ALARM = "show_alarm"
    }
}
