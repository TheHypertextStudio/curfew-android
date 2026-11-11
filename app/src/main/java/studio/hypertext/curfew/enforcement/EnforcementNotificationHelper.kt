package studio.hypertext.curfew.enforcement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.R

object EnforcementNotificationHelper {
    const val ENFORCEMENT_CHANNEL_ID = "curfew_enforcement"
    const val ENFORCEMENT_NOTIFICATION_ID = 4001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ENFORCEMENT_CHANNEL_ID,
            context.getString(R.string.enforcement_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.enforcement_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun showNotification(context: Context, session: EnforcementSession) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context)

        val title = when (session.purpose) {
            EnforcementPurpose.BEDTIME -> context.getString(R.string.enforcement_notification_bedtime_title)
            EnforcementPurpose.PROTECTED_WORK -> context.getString(R.string.enforcement_notification_work_title)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = CurfewAccessibilityService.ACTION_SHOW_ENFORCEMENT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val emergencyStopIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, EnforcementEmergencyReceiver::class.java).apply {
                action = EnforcementEmergencyReceiver.ACTION_EMERGENCY_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ENFORCEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock_rounded)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.enforcement_notification_body))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(
                0,
                context.getString(R.string.enforcement_notification_action_stop),
                emergencyStopIntent,
            )
            .build()

        manager.notify(ENFORCEMENT_NOTIFICATION_ID, notification)
    }

    fun dismissNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(ENFORCEMENT_NOTIFICATION_ID)
    }
}
