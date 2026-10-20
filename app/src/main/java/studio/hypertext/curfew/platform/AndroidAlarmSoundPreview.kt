package studio.hypertext.curfew.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A bounded rehearsal uses the same alarm usage and default sound as ringing. */
class AndroidAlarmSoundPreview(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var timeout: Runnable? = null

    suspend fun play(): Boolean {
        stop()
        val sound = withContext(Dispatchers.IO) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                isLooping = true
            }
        } ?: return false
        ringtone = sound
        sound.play()
        repeat(20) {
            if (sound.isPlaying) {
                timeout = Runnable { if (ringtone === sound) stop() }.also { handler.postDelayed(it, 2_000) }
                return true
            }
            delay(50)
        }
        stop()
        return false
    }

    fun stop() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        ringtone?.stop()
        ringtone = null
    }
}
