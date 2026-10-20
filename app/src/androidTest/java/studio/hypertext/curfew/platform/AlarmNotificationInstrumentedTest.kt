package studio.hypertext.curfew.platform

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.persistence.CurfewDatabase

class AlarmNotificationInstrumentedTest {
    @Test
    fun ringingNotificationShowsTheSavedCheckInRatherThanTheInternalAttempt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        val runtime = AlarmRuntimeRepository(context)
        val callbacks = CallbackConfigurationRepository(context)
        val definition = callbacks.save("Morning stretch", "https://fixture.example.test/wake", null)
        val id = "notification-${UUID.randomUUID()}"
        val now = Instant.now()
        try {
            runtime.save(AlarmCampaignEngine().advanceTo(
                AlarmCampaignState.Scheduled(id, now, AlarmRecurrencePolicy.default(), setOf("fixture")), now,
            ), callbackId = definition.callbackId)
            ContextCompat.startForegroundService(context, PerpetualAlarmService.startIntent(context, id, 1))
            val manager = context.getSystemService(NotificationManager::class.java)
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            var notification: Notification? = null
            do {
                notification = manager.activeNotifications.firstOrNull { it.notification.channelId == "curfew_perpetual_alarm" }?.notification
                if (notification != null) break
                Thread.sleep(100)
            } while (android.os.SystemClock.elapsedRealtime() < deadline)
            assertTrue("The alarm notification did not appear", notification != null)
            assertEquals("Time to get up.", notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            assertEquals("Waiting for Morning stretch", notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        } finally {
            context.stopService(Intent(context, PerpetualAlarmService::class.java))
            AndroidAlarmClockGateway(context).cancel(id)
            CurfewDatabase.open(context).alarmCampaignDao().delete(id)
            callbacks.delete(definition.callbackId)
        }
    }
}
