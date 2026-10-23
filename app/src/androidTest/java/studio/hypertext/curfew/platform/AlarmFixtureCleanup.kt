package studio.hypertext.curfew.platform

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.persistence.CurfewDatabase

@Suppress("DEPRECATION")
internal suspend fun cleanupAlarmFixture(context: Context, campaignId: String) {
    val gateway = AndroidAlarmClockGateway(context)
    val dao = CurfewDatabase.open(context).alarmCampaignDao()
    SunriseScheduler.serialized {
        check(dao.pending().none { it.campaignId != campaignId }) { "Fixture cleanup must not stop another pending alarm" }
        gateway.cancel(campaignId)
        context.stopService(Intent(context, PerpetualAlarmService::class.java))
        dao.delete(campaignId)
        CallbackPollScheduler.cancel(context, campaignId)
    }
    val manager = context.getSystemService(ActivityManager::class.java)
    fun serviceRunning() = manager.getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == PerpetualAlarmService::class.java.name }
    val deadline = SystemClock.elapsedRealtime() + 5_000
    while (serviceRunning() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
    assertFalse("The fixture playback service must finish before the next test", serviceRunning())
    // A transition already delivered before cancellation can finish during service shutdown.
    SunriseScheduler.serialized {
        gateway.cancel(campaignId)
        dao.delete(campaignId)
        CallbackPollScheduler.cancel(context, campaignId)
    }
    assertNull("Fixture state must stay absent after restoration finishes", dao.find(campaignId))
}
