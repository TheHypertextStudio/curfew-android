package studio.hypertext.curfew.platform

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
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
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.R

class AlarmNotificationInstrumentedTest {
    @Test
    fun ringingNotificationShowsTheSavedCheckInRatherThanTheInternalAttempt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = AlarmRuntimeRepository(context)
        check(runtime.pending().isEmpty()) { "Fixture setup must not change another pending alarm" }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        val callbacks = CallbackConfigurationRepository(context)
        val definition = callbacks.save("Morning stretch", "https://fixture.example.test/wake", null)
        val id = "notification-${UUID.randomUUID()}"
        val now = Instant.now()
        val manager = context.getSystemService(NotificationManager::class.java)
        PerpetualAlarmService.ensureChannels(context)
        val previousTag = "fixture-$id"
        val previous = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).setAction(previousTag),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(previousTag, 0, NotificationCompat.Builder(context, "curfew_perpetual_alarm")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Time to get up.")
            .setContentText("Waiting for your check-in").setContentIntent(previous).build())
        val handler = Handler(Looper.getMainLooper())
        val start = Runnable { ContextCompat.startForegroundService(context, PerpetualAlarmService.startIntent(context, id, 1)) }
        try {
            runtime.save(AlarmCampaignEngine().advanceTo(
                AlarmCampaignState.Scheduled(id, now, AlarmRecurrencePolicy.default(), setOf("fixture")), now,
            ), callbackId = definition.callbackId)
            // Hold the old notification long enough to expose selecting another occurrence.
            handler.postDelayed(start, 250)
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            var notification: Notification? = null
            do {
                val owner = PendingIntent.getActivity(context, id.hashCode(), Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                notification = manager.activeNotifications.firstOrNull {
                    owner != null && it.notification.channelId == "curfew_perpetual_alarm" && it.notification.contentIntent == owner
                }?.notification
                if (notification != null) break
                Thread.sleep(100)
            } while (android.os.SystemClock.elapsedRealtime() < deadline)
            assertTrue("The alarm notification did not appear", notification != null)
            assertEquals("Time to get up.", notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            assertEquals("Waiting for Morning stretch", notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        } finally {
            handler.removeCallbacks(start)
            try {
                cleanupAlarmFixture(context, id)
            } finally {
                manager.cancel(previousTag, 0)
                previous.cancel()
                callbacks.delete(definition.callbackId)
            }
        }
    }
}
