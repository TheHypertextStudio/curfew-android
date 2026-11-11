package studio.hypertext.curfew.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import studio.hypertext.curfew.MainActivity

class CurfewAccessibilityService : AccessibilityService() {

    private val engine = EnforcementEngine()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeSession: EnforcementSession? = null
    private lateinit var preferences: EnforcementPreferences

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = EnforcementPreferences(this)
        EnforcementNotificationHelper.ensureChannel(this)

        preferences.sessionFlow
            .onEach { session ->
                activeSession = session
                if (session != null && session.state == EnforcementSessionState.ACTIVE) {
                    engine.resetDisarmedState()
                    EnforcementNotificationHelper.showNotification(this, session)
                } else {
                    EnforcementNotificationHelper.dismissNotification(this)
                }
            }
            .launchIn(serviceScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        val session = activeSession ?: return
        if (session.state != EnforcementSessionState.ACTIVE) return

        val safetyContext = buildSafetyContext()
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        engine.processEvent(
            packageName = packageName,
            session = session,
            safetyContext = safetyContext,
            currentEpochMs = nowEpoch,
            currentElapsedRealtimeMs = nowElapsed,
            onIntercept = {
                // Request Home
                performGlobalAction(GLOBAL_ACTION_HOME)

                // Launch Curfew to foreground to display session
                val curfewIntent = Intent(this, MainActivity::class.java).apply {
                    action = ACTION_SHOW_ENFORCEMENT
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                    putExtra(EXTRA_INTERCEPTED_PACKAGE, packageName)
                }
                startActivity(curfewIntent)
            },
        )
    }

    override fun onInterrupt() {
        // Accessibility feedback interrupted
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        EnforcementNotificationHelper.dismissNotification(this)
        super.onDestroy()
    }

    private fun buildSafetyContext(): SafetyContext {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val defaultDialer = telecomManager?.defaultDialerPackage
        val defaultSms = runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()

        val homePackages = mutableSetOf<String>()
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = packageManager.queryIntentActivities(homeIntent, 0)
        for (info in resolveInfos) {
            info.activityInfo?.packageName?.let { homePackages.add(it) }
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val inCall = audioManager?.mode in setOf(
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_CALL_SCREENING,
        )

        return SafetyContext(
            curfewPackage = packageName,
            defaultDialerPackage = defaultDialer,
            defaultSmsPackage = defaultSms,
            homePackages = homePackages,
            activeNavigationPackages = emptySet(),
            autofillServicePackages = emptySet(),
            isInCall = inCall,
        )
    }

    companion object {
        const val ACTION_SHOW_ENFORCEMENT = studio.hypertext.curfew.ACTION_SHOW_ENFORCEMENT
        const val EXTRA_INTERCEPTED_PACKAGE = "studio.hypertext.curfew.extra.INTERCEPTED_PACKAGE"

        @Volatile
        private var instance: CurfewAccessibilityService? = null

        fun disarmActiveSession() {
            instance?.engine?.emergencyDisarmInMemory()
        }
    }
}
