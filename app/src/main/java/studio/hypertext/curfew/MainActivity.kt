package studio.hypertext.curfew

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import studio.hypertext.curfew.account.BrowserOAuthEnrollment
import studio.hypertext.curfew.config.ProductionServices
import studio.hypertext.curfew.enforcement.CurfewAccessibilityService
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway
import studio.hypertext.curfew.platform.PerpetualAlarmService
import studio.hypertext.curfew.sync.WakeSyncScheduler
import studio.hypertext.curfew.ui.CurfewApp
import studio.hypertext.curfew.ui.CurfewViewModel
import studio.hypertext.curfew.ui.PlaceCheckInHost
import studio.hypertext.curfew.ui.theme.CurfewTheme

/**
 * Lean system integration activity for Curfew.
 * Serves exclusively as an entry point for Android platform windowing, permissions,
 * and system intents, delegating all domain logic and UI state to ViewModels and Composables.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: CurfewViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* system managed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        PerpetualAlarmService.ensureChannels(this)
        WakeSyncScheduler.install(this)

        handleIntent(intent)

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

                LaunchedEffect(state.isAlarmRinging) {
                    if (state.isAlarmRinging) {
                        presentOverKeyguard()
                    } else {
                        dismissKeyguardFlags()
                    }
                }

                CurfewApp(
                    viewModel = viewModel,
                    onGrantExactAlarm = ::requestExactAlarmAccess,
                    onGrantNotifications = ::requestNotifications,
                    onGrantFullScreen = ::requestFullScreenAccess,
                    onRepairAlarmChannel = ::openAlarmChannelSettings,
                    onRaiseAlarmVolume = ::openSoundSettings,
                    onOpenAccount = ::openAccountPortal,
                    onEnrollAccount = ::openOAuthEnrollment,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
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
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            if (viewModel.handleOAuthCallback(uri)) {
                setIntent(Intent(this, MainActivity::class.java))
                return
            }
        }
        if (intent.action == CurfewAccessibilityService.ACTION_SHOW_ENFORCEMENT) {
            val intercepted = intent.getStringExtra(CurfewAccessibilityService.EXTRA_INTERCEPTED_PACKAGE)
            viewModel.setInterceptedPackage(intercepted)
        }
        val requestedId = intent.getStringExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID)
        if (intent.getBooleanExtra(EXTRA_SHOW_ALARM, false) && requestedId != null) {
            viewModel.loadRequestedAlarm(requestedId)
        }
    }

    private fun presentOverKeyguard() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun dismissKeyguardFlags() {
        setShowWhenLocked(false)
        setTurnScreenOn(false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun requestExactAlarmAccess() {
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestFullScreenAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openAlarmChannelSettings() {
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, PerpetualAlarmService.ALARM_CHANNEL_ID),
        )
    }

    private fun openSoundSettings() {
        startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAccountPortal() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ProductionServices.ACCOUNT_PORTAL)))
    }

    private fun openOAuthEnrollment() {
        lifecycleScope.launch {
            try {
                BrowserOAuthEnrollment(this@MainActivity).begin()
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
