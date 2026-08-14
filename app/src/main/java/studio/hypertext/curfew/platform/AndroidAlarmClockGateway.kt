package studio.hypertext.curfew.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant
import studio.hypertext.curfew.MainActivity

class AndroidAlarmClockGateway(context: Context) : AlarmClockGateway {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    override fun setAlarmClock(campaignId: String, triggerAt: Instant) {
        check(canScheduleExactAlarms()) { "exact alarm permission is unavailable" }
        val showIntent = PendingIntent.getActivity(
            applicationContext,
            campaignId.hashCode(),
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_ALARM, true)
                .putExtra(EXTRA_CAMPAIGN_ID, campaignId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt.toEpochMilli(), showIntent),
            transitionIntent(campaignId),
        )
    }

    override fun cancel(campaignId: String) {
        alarmManager.cancel(transitionIntent(campaignId))
    }

    private fun transitionIntent(campaignId: String): PendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        campaignId.hashCode(),
        Intent(applicationContext, AlarmTransitionReceiver::class.java)
            .setAction(ACTION_ALARM_TRANSITION)
            .putExtra(EXTRA_CAMPAIGN_ID, campaignId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_ALARM_TRANSITION = "studio.hypertext.curfew.action.ALARM_TRANSITION"
        const val EXTRA_CAMPAIGN_ID = "campaign_id"
    }
}
