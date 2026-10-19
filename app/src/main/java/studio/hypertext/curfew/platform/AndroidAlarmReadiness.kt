package studio.hypertext.curfew.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import studio.hypertext.curfew.persistence.CurfewPreferenceState
import studio.hypertext.curfew.readiness.AlarmCapabilities
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.AlarmReadinessEvaluator

class AndroidAlarmReadiness(context: Context) {
    private val context = context.applicationContext

    fun evaluate(preferences: CurfewPreferenceState): AlarmReadiness {
        val notifications = context.getSystemService(NotificationManager::class.java)
        return AlarmReadinessEvaluator().evaluate(
            AlarmCapabilities(
                canScheduleExactAlarms = context.getSystemService(AlarmManager::class.java)
                    .canScheduleExactAlarms(),
                notificationsGranted = notifications.areNotificationsEnabled() &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED,
                fullScreenIntentAllowed = Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent(),
                alarmChannelAudible = PerpetualAlarmService.alarmChannelIsAudible(context),
                alarmVolumeAudible = context.getSystemService(AudioManager::class.java)
                    .getStreamVolume(AudioManager.STREAM_ALARM) > 0,
                testSoundCompleted = preferences.testSoundCompleted,
                limitationsAcknowledged = preferences.readinessAcknowledged,
            ),
        )
    }
}
