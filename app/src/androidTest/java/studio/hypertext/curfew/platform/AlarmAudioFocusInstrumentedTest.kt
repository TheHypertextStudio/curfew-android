package studio.hypertext.curfew.platform

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy

class AlarmAudioFocusInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio = context.getSystemService(AudioManager::class.java)
    private fun audioActive(): Boolean {
        val dump = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys audio").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().toString(Charsets.UTF_8)
            }
        return hasStartedAlarmPlayer(dump, context.applicationInfo.uid)
    }

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
        assertTrue(description, predicate())
    }

    @Test
    fun transientFocusLossResumesTheSameRingingAttempt() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (command in listOf(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
            "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow",
        )) instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        val id = UUID.randomUUID().toString()
        val repository = AlarmRuntimeRepository(context)
        val now = Instant.now()
        val ringing = AlarmCampaignEngine().advanceTo(
            AlarmCampaignState.Scheduled(id, now, AlarmRecurrencePolicy.default(), setOf("fixture")), now,
        )
        val competingFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener({}, Handler(Looper.getMainLooper()))
            .build()
        try {
            repository.save(ringing)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { ContextCompat.startForegroundService(it, PerpetualAlarmService.startIntent(it, id, 1)) }
                await("The saved alarm must produce audio", ::audioActive)
                var granted = AudioManager.AUDIOFOCUS_REQUEST_FAILED
                scenario.onActivity { granted = audio.requestAudioFocus(competingFocus) }
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, granted)
                await("Transient focus loss must pause alarm audio") { !audioActive() }
                assertEquals("Focus loss must not release or advance the occurrence", ringing, repository.load(id))
                audio.abandonAudioFocusRequest(competingFocus)
                await("Returning focus must resume alarm audio", ::audioActive)
                assertEquals("Resuming must retain the original attempt", ringing, repository.load(id))
            }
        } finally {
            audio.abandonAudioFocusRequest(competingFocus)
            cleanupAlarmFixture(context, id)
        }
    }
}
