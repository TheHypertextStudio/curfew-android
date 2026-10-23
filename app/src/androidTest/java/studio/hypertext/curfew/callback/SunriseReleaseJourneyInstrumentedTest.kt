package studio.hypertext.curfew.callback

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.testing.TestListenableWorkerBuilder
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway
import studio.hypertext.curfew.platform.SunriseScheduler

class SunriseReleaseJourneyInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val runtime = AlarmRuntimeRepository(context)
    private val scheduler = SunriseScheduler(context)

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    private suspend fun await(description: String, condition: suspend () -> Boolean) {
        repeat(200) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("Timed out: $description; pending=${runtime.pending()}; " +
            "notification=${ringingNotification()}; alarmAudio=${alarmAudioIsActive()}; " +
            "exactAccess=${AndroidAlarmClockGateway(context).canScheduleExactAlarms()}; " +
            "readiness=${studio.hypertext.curfew.platform.AndroidAlarmReadiness(context).evaluate(studio.hypertext.curfew.persistence.CurfewPreferences(context).state.first())}")
    }

    private fun ringingNotification() = context.getSystemService(NotificationManager::class.java)
        .activeNotifications.any { it.notification.channelId == "curfew_perpetual_alarm" }

    private fun alarmAudioIsActive() = context.getSystemService(AudioManager::class.java)
        .activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }

    @Test
    fun scheduleRingsPendingAndInvalidApprovalStayActiveAndValidApprovalStopsPlayback() = runBlocking {
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            shell("appops set ${context.packageName} USE_FULL_SCREEN_INTENT allow")
        }
        val preferences = CurfewPreferences(context)
        // These fixtures satisfy stored readiness only; this emulator test cannot establish physical sound.
        preferences.acknowledgeLimitations()
        preferences.setTestSoundConfirmed(true)
        val setup = AndroidWakeConditionSetup(context)
        var campaignId: String? = null
        var callbackId: String? = null
        LocalHttpsVerifier().use { verifier ->
            try {
                val definition = setup.save("Morning check-in", verifier.endpoint, null)
                callbackId = definition.callbackId
                verifier.definition = definition
                assertTrue(setup.verify(definition.callbackId))
                assertTrue(preferences.state.first().isCallbackReady(definition.callbackId))
                await("granted setup capabilities become available") {
                    studio.hypertext.curfew.platform.AndroidAlarmReadiness(context).evaluate(preferences.state.first()).canArm
                }
                val start = Instant.now().plusSeconds(4).atZone(ZoneOffset.UTC)
                val saved = scheduler.save(SunriseSchedule(
                    id = UUID.randomUUID().toString(),
                    wakeTime = start.toLocalTime(),
                    onceDate = start.toLocalDate(),
                    weekdays = emptySet(),
                    enabled = true,
                    callbackId = definition.callbackId,
                ), zone = ZoneOffset.UTC)
                val id = requireNotNull(saved.occurrence).campaignId
                campaignId = id
                await("scheduled alarm starts through Android AlarmManager") {
                    runtime.load(id) is AlarmCampaignState.Ringing && ringingNotification() && alarmAudioIsActive()
                }
                for (mode in listOf("pending", "forged", "malformed", "wrong-occurrence", "expired", "future", "replay-seed", "replayed-approval", "unavailable")) {
                    verifier.mode = mode
                    val worker = TestListenableWorkerBuilder<CallbackPollWorker>(context)
                        .setInputData(Data.Builder().putString(CallbackPollWorker.KEY_CAMPAIGN_ID, id).build())
                        .build()
                    worker.doWork()
                    assertTrue("$mode must not release Sunrise", runtime.load(id) is AlarmCampaignState.Ringing)
                    assertTrue("$mode stopped playback presentation", ringingNotification())
                    assertTrue("$mode stopped alarm audio", alarmAudioIsActive())
                }
                verifier.mode = "satisfied"
                val worker = TestListenableWorkerBuilder<CallbackPollWorker>(context)
                    .setInputData(Data.Builder().putString(CallbackPollWorker.KEY_CAMPAIGN_ID, id).build())
                    .build()
                worker.doWork()
                await("authenticated release stops foreground playback") {
                    runtime.load(id) is AlarmCampaignState.Satisfied && !ringingNotification() && !alarmAudioIsActive()
                }
                assertEquals("Morning check-in", (runtime.load(id) as AlarmCampaignState.Satisfied).conditionLabel)
                assertEquals(null, scheduler.state.first()?.occurrence)
                assertNotNull(scheduler.state.first()?.completedThrough)
                assertTrue("Expected actual TLS callback exchanges", verifier.requests.get() >= 11)
                worker.doWork()
                assertFalse(ringingNotification())
            } finally {
                campaignId?.let { id ->
                    val remaining = runtime.load(id)
                    if (remaining is AlarmCampaignState.Ringing || remaining is AlarmCampaignState.Quiet) {
                        runtime.save(studio.hypertext.curfew.alarm.AlarmCampaignEngine().override(
                            remaining, Instant.now(), "test-cleanup", Instant.now().plusSeconds(60),
                        ))
                    }
                    scheduler.consume(id)
                    scheduler.state.first()?.takeIf { it.occurrence?.campaignId == id }?.let {
                        scheduler.save(it.schedule.copy(enabled = false))
                    }
                    AndroidAlarmClockGateway(context).cancel(id)
                    CallbackPollScheduler.cancel(context, id)
                    context.stopService(android.content.Intent(context, studio.hypertext.curfew.platform.PerpetualAlarmService::class.java))
                    CurfewDatabase.open(context).alarmCampaignDao().delete(id)
                }
                callbackId?.let { CallbackConfigurationRepository(context).delete(it) }
            }
        }
    }
}
