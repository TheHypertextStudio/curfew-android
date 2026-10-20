package studio.hypertext.curfew.platform

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.testing.TestListenableWorkerBuilder
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.callback.AndroidWakeConditionSetup
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.callback.CallbackPollWorker
import studio.hypertext.curfew.callback.LocalHttpsVerifier
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.persistence.CurfewPreferences

/** Opt-in wall-clock acceptance; default CI runs must not count its skipped case as evidence. */
class PersistentCadenceJourneyInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val runtime = AlarmRuntimeRepository(context)
    private val scheduler = SunriseScheduler(context)
    private val marker get() = context.cacheDir.resolve("cadence-fixture.json")
    private val timeline get() = context.cacheDir.resolve("cadence-timeline.jsonl")

    private fun requireOptIn() = assumeTrue(
        "Run through the controlled emulator acceptance script",
        InstrumentationRegistry.getArguments().getString("persistentCadenceAcceptance") == "true",
    )

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().toString(Charsets.UTF_8)
    }

    private fun audioActive() = context.getSystemService(AudioManager::class.java).activePlaybackConfigurations
        .any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }

    private fun offline() = shell("dumpsys connectivity").contains("Active default network: none")

    private suspend fun await(description: String, timeoutMillis: Long, predicate: suspend () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            Thread.sleep(200)
        }
        error("Timed out waiting for $description")
    }

    private fun record(phase: String, state: AlarmCampaignState) {
        val entry = JSONObject().put("phase", phase).put("observedAt", Instant.now().toString())
            .put("elapsedMillis", SystemClock.elapsedRealtime()).put("campaignId", state.campaignId)
            .put("alarmAudioActive", audioActive()).put("offline", offline())
        when (state) {
            is AlarmCampaignState.Ringing -> entry.put("attempt", state.attempt).put("ringStartedAt", state.ringStartedAt.toString()).put("ringEndsAt", state.ringEndsAt.toString())
            is AlarmCampaignState.Quiet -> entry.put("completedAttempt", state.completedAttempt).put("nextAttemptAt", state.nextAttemptAt.toString())
            else -> Unit
        }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            context.cacheDir.resolve("cadence-$phase.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        timeline.appendText("$entry\n")
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nCadence evidence: $entry\n") })
    }

    @Test(timeout = 240_000)
    fun scheduledAlarmKeepsDefaultCadenceOffline(): Unit = runBlocking {
        requireOptIn()
        assertTrue("The driver must disconnect the emulator", offline())
        assertTrue("The fixture must not replace a pending alarm", runtime.pending().isEmpty())
        val previous = scheduler.state.first()
        assertTrue("The fixture must not replace a saved upcoming alarm", previous?.occurrence == null || !previous.schedule.enabled)
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        val preferences = CurfewPreferences(context)
        preferences.acknowledgeLimitations()
        preferences.setTestSoundConfirmed(true)
        timeline.writeText("")
        val setup = AndroidWakeConditionSetup(context)
        var campaignId: String? = null
        var callbackId: String? = null
        try {
            val definition = LocalHttpsVerifier().use { verifier ->
                setup.save("Cadence fixture check-in", verifier.endpoint, null).also {
                    callbackId = it.callbackId
                    verifier.definition = it
                    assertTrue(setup.verify(it.callbackId))
                }
            }
            // The validated peer is now closed. It cannot supply any new approval.
            val start = Instant.now().plusSeconds(4).atZone(ZoneOffset.UTC)
            val scheduleId = "cadence-${UUID.randomUUID()}"
            val saved = scheduler.save(SunriseSchedule(scheduleId, start.toLocalTime(), start.toLocalDate(), emptySet(), true, definition.callbackId), zone = ZoneOffset.UTC)
            val id = requireNotNull(saved.occurrence).campaignId
            campaignId = id
            marker.writeText(JSONObject().put("scheduleId", scheduleId).put("campaignId", id).put("callbackId", definition.callbackId).toString())
            await("first alarm-clock delivery and audio", 20_000) { runtime.load(id) is AlarmCampaignState.Ringing && audioActive() }
            val first = runtime.load(id) as AlarmCampaignState.Ringing
            assertEquals(1, first.attempt)
            assertEquals(Duration.ofMinutes(2), first.policy.ringDuration)
            assertEquals(Duration.ofMinutes(1), first.policy.quietDuration)
            record("first-ring", first)
            TestListenableWorkerBuilder<CallbackPollWorker>(context)
                .setInputData(Data.Builder().putString(CallbackPollWorker.KEY_CAMPAIGN_ID, id).build()).build().doWork()
            assertTrue("Unavailable verification must leave ringing active", runtime.load(id) is AlarmCampaignState.Ringing)
            assertTrue(audioActive())
            await("real two-minute boundary and quiet audio", 140_000) { runtime.load(id) is AlarmCampaignState.Quiet && !audioActive() }
            val quiet = runtime.load(id) as AlarmCampaignState.Quiet
            assertEquals(1, quiet.completedAttempt)
            assertEquals(first.ringEndsAt.plusSeconds(60), quiet.nextAttemptAt)
            assertFalse("The ring must not end early", Instant.now() < first.ringEndsAt)
            record("quiet", quiet)
            Thread.sleep(5_000)
            assertTrue("Quiet is still an active occurrence", runtime.load(id) is AlarmCampaignState.Quiet)
            assertFalse(audioActive())
            await("second alarm-clock delivery and audio", 75_000) {
                (runtime.load(id) as? AlarmCampaignState.Ringing)?.attempt == 2 && audioActive()
            }
            val second = runtime.load(id) as AlarmCampaignState.Ringing
            assertEquals(first.campaignId, second.campaignId)
            assertEquals(quiet.nextAttemptAt, second.ringStartedAt)
            assertTrue("The phone must remain offline across the cadence", offline())
            record("second-ring", second)
        } finally {
            cleanup(campaignId, callbackId)
        }
    }

    @Test fun cleanupInterruptedFixture(): Unit = runBlocking {
        requireOptIn()
        if (!marker.exists()) return@runBlocking
        val fixture = JSONObject(marker.readText())
        require(fixture.getString("scheduleId").startsWith("cadence-"))
        cleanup(fixture.getString("campaignId"), fixture.getString("callbackId"))
    }

    private suspend fun cleanup(campaignId: String?, callbackId: String?) {
        campaignId?.let { id ->
            val state = runtime.load(id)
            if (state is AlarmCampaignState.Scheduled && Instant.now() < state.startsAt) {
                scheduler.state.first()?.takeIf { it.occurrence?.campaignId == id }?.let { scheduler.save(it.schedule.copy(enabled = false)) }
            } else if (state is AlarmCampaignState.Ringing || state is AlarmCampaignState.Quiet || state is AlarmCampaignState.Scheduled) {
                runtime.save(AlarmCampaignEngine().override(requireNotNull(state), Instant.now(), "cadence-test-cleanup", Instant.now().plusSeconds(60)))
            }
            scheduler.consume(id)
            scheduler.state.first()?.takeIf { it.schedule.id.startsWith("cadence-") }?.let { scheduler.save(it.schedule.copy(enabled = false)) }
            AndroidAlarmClockGateway(context).cancel(id)
            CallbackPollScheduler.cancel(context, id)
            context.stopService(Intent(context, PerpetualAlarmService::class.java))
            CurfewDatabase.open(context).alarmCampaignDao().delete(id)
        }
        callbackId?.let { CallbackConfigurationRepository(context).delete(it) }
    }
}
