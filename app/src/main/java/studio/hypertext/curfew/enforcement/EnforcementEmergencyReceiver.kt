package studio.hypertext.curfew.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EnforcementEmergencyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EMERGENCY_STOP) return

        // 1. Immediately disarm in-memory actuator to prevent any further interception
        CurfewAccessibilityService.disarmActiveSession()

        // 2. Dismiss notification
        EnforcementNotificationHelper.dismissNotification(context)

        // 3. Persist cancellation
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val preferences = EnforcementPreferences(context.applicationContext)
                preferences.emergencyStop(reason = "emergency_receiver")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_EMERGENCY_STOP = "studio.hypertext.curfew.action.EMERGENCY_STOP"
    }
}
