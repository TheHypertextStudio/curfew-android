package studio.hypertext.curfew.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.test.core.app.ApplicationProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

/** These probes distinguish emulator audio output from an unreadable default alarm. */
class AlarmAudioProbeInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    private fun awaitPlayback(): Boolean {
        val manager = context.getSystemService(AudioManager::class.java)
        repeat(40) {
            if (manager.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }) return true
            Thread.sleep(50)
        }
        return false
    }

    @Test
    fun aKnownPcmFileStartsAnAlarmAudioTrack() {
        val samples = 22_050
        val pcm = ByteArray(samples * 2)
        val data = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        repeat(samples) { data.putShort((sin(2 * PI * 660 * it / samples) * 8_000).toInt().toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(samples)
            .putInt(samples * 2).putShort(2.toShort()).putShort(16.toShort())
            .put("data".toByteArray()).putInt(pcm.size)
        val file = java.io.File.createTempFile("alarm-probe-", ".wav", context.cacheDir)
        val player = MediaPlayer()
        try {
            file.outputStream().use { it.write(header.array()); it.write(pcm) }
            player.setAudioAttributes(attributes)
            player.setDataSource(file.absolutePath)
            player.isLooping = true
            player.prepare()
            player.start()
            assertTrue("Known PCM audio must be playing", player.isPlaying)
            assertTrue("Android must report the alarm audio track", awaitPlayback())
        } finally {
            player.release()
            file.delete()
        }
    }

    @Test
    fun theDefaultRingtoneStartsAnAlarmAudioTrack() {
        val ringtone = requireNotNull(RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)))
        try {
            ringtone.audioAttributes = attributes
            ringtone.isLooping = true
            ringtone.play()
            assertTrue("Default ringtone must produce an alarm audio track", awaitPlayback())
        } finally {
            ringtone.stop()
        }
    }
}
