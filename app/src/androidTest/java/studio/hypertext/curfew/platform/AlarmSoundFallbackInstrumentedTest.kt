package studio.hypertext.curfew.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSoundFallbackInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val missing = Uri.parse("content://studio.hypertext.curfew.missing/sound")

    private fun audioActive() = audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }
    private fun await(description: String, predicate: () -> Boolean) {
        repeat(100) { if (predicate()) return; Thread.sleep(50) }
        error("Timed out: $description")
    }

    @Test fun anUnreadablePreferredSoundFallsBackToBundledAlarmAudio() = runBlocking {
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val sound = AndroidAlarmSound(context, missing)
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, maxOf(originalVolume, 1), 0)
            assertTrue(sound.play())
            await("bundled alarm audio") { audioActive() }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { sound.stop() }
            audio.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
        }
        await("audio stop") { !audioActive() }
    }

    @Test fun rehearsalUsesTheSameFallbackAndStopsAfterItsTimeout() = runBlocking {
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val preview = AndroidAlarmSoundPreview(context, missing)
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, maxOf(originalVolume, 1), 0)
            assertTrue(preview.play())
            await("preview fallback audio") { audioActive() }
            await("bounded preview stop") { !audioActive() }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { preview.stop() }
            audio.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
        }
    }
}
