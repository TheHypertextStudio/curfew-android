package studio.hypertext.curfew.platform

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.MainThread
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import studio.hypertext.curfew.R
import studio.hypertext.curfew.alarm.AlarmSoundPlayback
import studio.hypertext.curfew.alarm.AlarmSoundSource

/** Both rehearsal and persistent playback own their audio track instead of delegating to SystemUI. */
internal class AndroidAlarmSound(
    context: Context,
    private val preferredUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
) {
    private val context = context.applicationContext
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val playback = AlarmSoundPlayback(
        preferred = { prepare(preferredUri) },
        fallback = { prepare(Uri.parse("android.resource://${this.context.packageName}/${R.raw.sunrise_fallback}")) },
        awaitPlaying = wait@{ sound ->
            repeat(20) {
                if (sound.isPlaying) return@wait true
                delay(50)
            }
            false
        },
    )
    val isPlaying: Boolean get() = playback.isPlaying

    suspend fun play(): Boolean = withContext(Dispatchers.Main.immediate) { playback.play() }

    @MainThread
    fun stop() = playback.stop()

    private suspend fun prepare(uri: Uri): AlarmSoundSource {
        var descriptor: AssetFileDescriptor? = null
        try {
            withContext(Dispatchers.IO) {
                descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    ?: throw IOException("Alarm sound is unavailable")
            }
            val source = requireNotNull(descriptor)
            val player = MediaPlayer()
            try {
                player.setAudioAttributes(attributes)
                if (source.declaredLength < 0) player.setDataSource(source.fileDescriptor)
                else player.setDataSource(source.fileDescriptor, source.startOffset, source.declaredLength)
                player.isLooping = true
                val prepared = CompletableDeferred<Unit>()
                player.setOnPreparedListener { prepared.complete(Unit) }
                player.setOnErrorListener { _, _, _ ->
                    prepared.completeExceptionally(IOException("Alarm sound decoding failed"))
                    true
                }
                player.prepareAsync()
                if (withTimeoutOrNull(3_000) { prepared.await(); true } != true) {
                    throw IOException("Alarm sound preparation timed out")
                }
                return PlayerSource(player)
            } catch (failure: Throwable) {
                player.release()
                throw failure
            }
        } finally {
            // A provider can finish opening after cancellation; close its descriptor in that case too.
            withContext(NonCancellable + Dispatchers.IO) { descriptor?.close() }
        }
    }

    private class PlayerSource(private val player: MediaPlayer) : AlarmSoundSource {
        private var released = false
        override val isPlaying: Boolean get() = !released && runCatching { player.isPlaying }.getOrDefault(false)
        override fun play() { if (!released) player.start() }
        override fun stop() {
            if (released) return
            released = true
            player.release()
        }
    }
}
