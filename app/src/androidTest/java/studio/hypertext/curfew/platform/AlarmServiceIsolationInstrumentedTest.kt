package studio.hypertext.curfew.platform

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.sync.WakeStatusPollWorker
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AlarmServiceIsolationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = AlarmRuntimeRepository(context)
    private val currentId = UUID.randomUUID().toString()
    private val oldId = UUID.randomUUID().toString()
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @After
    fun cleanup() =
        runBlocking {
            context.stopService(Intent(context, PerpetualAlarmService::class.java))
            for (id in listOf(currentId, oldId)) {
                AndroidAlarmClockGateway(context).cancel(id)
                CallbackPollScheduler.cancel(context, id)
                CurfewDatabase.open(context).alarmCampaignDao().delete(id)
            }
        }

    @Test
    fun alarmTransitionsUseForegroundDelivery() {
        val command = AndroidAlarmClockGateway(context).transitionCommand(currentId)
        assertTrue(
            "Alarm transitions must not wait in the background broadcast queue",
            command.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0,
        )
        assertEquals(currentId, command.getStringExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID))
        assertEquals(AlarmTransitionReceiver::class.java.name, command.component?.className)
    }

    @Test
    fun terminalCleanupForAnOldOccurrenceCannotStopCurrentPlayback() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            for (command in listOf(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow",
            )) {
                instrumentation.uiAutomation.executeShellCommand(command).use {
                    android.os.ParcelFileDescriptor
                        .AutoCloseInputStream(it)
                        .readBytes()
                }
            }
            val now = Instant.now()
            val engine = AlarmCampaignEngine()
            repository.save(
                engine.advanceTo(
                    AlarmCampaignState.Scheduled(currentId, now, AlarmRecurrencePolicy.default(), setOf("fixture")),
                    now,
                ),
            )
            repository.save(
                engine.satisfy(
                    AlarmCampaignState.Scheduled(oldId, now.minusSeconds(60), AlarmRecurrencePolicy.default(), setOf("fixture")),
                    now,
                    "Previous morning",
                ),
            )
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    ContextCompat.startForegroundService(
                        activity,
                        PerpetualAlarmService.startIntent(activity, currentId, 1),
                    )
                }
                val audio = context.getSystemService(AudioManager::class.java)

                fun audioActive() = audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }
                val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
                while ((!isRingingPresentationActive() || !audioActive()) &&
                    android.os.SystemClock.elapsedRealtime() < deadline
                ) {
                    Thread.sleep(50)
                }
                assertTrue("Current alarm did not produce audio", audioActive())
                assertEquals(
                    androidx.work.ListenableWorker.Result
                        .success(),
                    TestListenableWorkerBuilder<WakeStatusPollWorker>(context).build().completeCampaign(oldId),
                )
                instrumentation.waitForIdleSync()
                repeat(20) {
                    Thread.sleep(100)
                    assertTrue("Old terminal cleanup stopped current alarm audio", audioActive())
                    assertTrue("Old terminal cleanup removed current presentation", isRingingPresentationActive())
                }
            }
        }

    @Test
    fun aDelayedStartForAnOldOccurrenceCannotStopCurrentPlayback() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            for (command in listOf(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow",
            )) {
                instrumentation.uiAutomation.executeShellCommand(command).use {
                    android.os.ParcelFileDescriptor
                        .AutoCloseInputStream(it)
                        .readBytes()
                }
            }
            val now = Instant.now()
            val engine = AlarmCampaignEngine()
            repository.save(
                engine.advanceTo(
                    AlarmCampaignState.Scheduled(currentId, now, AlarmRecurrencePolicy.default(), setOf("fixture")),
                    now,
                ),
            )
            repository.save(
                engine.satisfy(
                    AlarmCampaignState.Scheduled(oldId, now.minusSeconds(60), AlarmRecurrencePolicy.default(), setOf("fixture")),
                    now,
                    "Previous morning",
                ),
            )
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    ContextCompat.startForegroundService(
                        activity,
                        PerpetualAlarmService.startIntent(activity, currentId, 1),
                    )
                }
                val audio = context.getSystemService(AudioManager::class.java)

                fun audioActive() = audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }
                val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
                while ((!isRingingPresentationActive() || !audioActive()) &&
                    android.os.SystemClock.elapsedRealtime() < deadline
                ) {
                    Thread.sleep(50)
                }
                assertTrue("Current alarm did not produce audio", audioActive())
                scenario.onActivity { activity ->
                    ContextCompat.startForegroundService(
                        activity,
                        PerpetualAlarmService.startIntent(activity, oldId, 1),
                    )
                }
                instrumentation.waitForIdleSync()
                repeat(20) {
                    Thread.sleep(100)
                    assertTrue("Old service start stopped current alarm audio", audioActive())
                    assertTrue("Old service start removed current presentation", isRingingPresentationActive())
                }
            }
        }

    private fun isRingingPresentationActive() =
        notifications.activeNotifications.any {
            it.notification.channelId == PerpetualAlarmService.ALARM_CHANNEL_ID
        }

    @Test
    fun aLateTransitionForAnOldReleasedAlarmCannotStopCurrentPlayback() =
        runBlocking {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                ).use {
                    android.os.ParcelFileDescriptor
                        .AutoCloseInputStream(it)
                        .readBytes()
                }
            val now = Instant.now()
            val engine = AlarmCampaignEngine()
            val current =
                engine.advanceTo(
                    AlarmCampaignState.Scheduled(currentId, now, AlarmRecurrencePolicy.default(), setOf("fixture")),
                    now,
                )
            repository.save(current)
            repository.save(
                engine.satisfy(
                    AlarmCampaignState.Scheduled(oldId, now.minusSeconds(60), AlarmRecurrencePolicy.default(), setOf("fixture")),
                    now,
                    "Previous morning",
                ),
            )
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    ContextCompat.startForegroundService(
                        activity,
                        PerpetualAlarmService.startIntent(activity, currentId, 1, null, null),
                    )
                }
                val audio = context.getSystemService(AudioManager::class.java)

                fun audioActive() = audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }
                val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
                while ((!isRingingPresentationActive() || !audioActive()) &&
                    android.os.SystemClock.elapsedRealtime() < deadline
                ) {
                    Thread.sleep(50)
                }
                val startupEvidence = "state=${repository.load(
                    currentId,
                )} channels=${notifications.activeNotifications.map { it.notification.channelId }} alarmAudio=${audioActive()}"
                assertTrue("Current alarm did not reach ringing presentation: $startupEvidence", isRingingPresentationActive())
                assertTrue("Current alarm did not produce audio: $startupEvidence", audioActive())
                val completed = CountDownLatch(1)
                context.sendOrderedBroadcast(
                    AndroidAlarmClockGateway(context).transitionCommand(oldId),
                    null,
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            completed.countDown()
                        }
                    },
                    Handler(Looper.getMainLooper()),
                    0,
                    null,
                    null,
                )
                assertTrue("Old transition did not finish", completed.await(10, TimeUnit.SECONDS))
                assertTrue("An old occurrence stopped current playback", isRingingPresentationActive())
                assertTrue("An old occurrence stopped current alarm audio", audioActive())
            }
        }
}
