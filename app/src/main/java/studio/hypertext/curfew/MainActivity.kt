package studio.hypertext.curfew

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import studio.hypertext.curfew.platform.PerpetualAlarmService
import studio.hypertext.curfew.platform.SystemSettingsLauncher
import studio.hypertext.curfew.sync.WakeSyncScheduler
import studio.hypertext.curfew.ui.CurfewApp
import studio.hypertext.curfew.ui.CurfewViewModel
import studio.hypertext.curfew.ui.PlaceCheckInHost
import studio.hypertext.curfew.ui.ShowOverKeyguardEffect
import studio.hypertext.curfew.ui.theme.CurfewTheme

/**
 * Lean system integration activity for Curfew.
 * Serves exclusively as an entry point for Android platform windowing, permissions,
 * and system intents, delegating all domain logic and UI state to ViewModels and Composables.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject internal lateinit var systemSettings: SystemSettingsLauncher

    private val viewModel: CurfewViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* system managed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        PerpetualAlarmService.ensureChannels(this)
        WakeSyncScheduler.install(this)

        forwardLaunch(intent)

        setContent {
            CurfewTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(viewModel) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.accountMessages.collect { message ->
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }

                ShowOverKeyguardEffect(this@MainActivity, active = state.isAlarmRinging)

                CurfewApp(
                    viewModel = viewModel,
                    onGrantExactAlarm = systemSettings::requestExactAlarmAccess,
                    onGrantNotifications = ::requestNotifications,
                    onGrantFullScreen = systemSettings::requestFullScreenAccess,
                    onRepairAlarmChannel = systemSettings::openAlarmChannel,
                    onRaiseAlarmVolume = systemSettings::openSoundSettings,
                    onOpenAccount = systemSettings::openAccountPortal,
                    onEnrollAccount = ::openOAuthEnrollment,
                    onOpenAccessibilitySettings = systemSettings::openAccessibilitySettings,
                    placeCheckInContent = { advanced, done, useForAlarm ->
                        PlaceCheckInHost(
                            selectedId = state.callbackId,
                            onAdvanced = advanced,
                            onDone = done,
                            onUseForAlarm = useForAlarm,
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onForegroundResume()
        PerpetualAlarmService.restoreAfterForegroundEntry(this)
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

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openOAuthEnrollment() {
        lifecycleScope.launch {
            try {
                systemSettings.enrollAccount()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, R.string.account_enrollment_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_SHOW_ALARM = "show_alarm"
    }
}
