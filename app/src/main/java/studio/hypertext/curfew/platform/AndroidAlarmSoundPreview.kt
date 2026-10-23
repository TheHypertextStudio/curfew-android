package studio.hypertext.curfew.platform

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A bounded rehearsal uses the same preferred and bundled sounds as ringing. */
class AndroidAlarmSoundPreview(
    context: Context,
    preferredUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
) {
    private val handler = Handler(Looper.getMainLooper())
    private val sound = AndroidAlarmSound(context, preferredUri)
    private var timeout: Runnable? = null

    suspend fun play(): Boolean = withContext(Dispatchers.Main.immediate) {
        stop()
        if (!sound.play()) return@withContext false
        timeout = Runnable { stop() }.also { handler.postDelayed(it, 2_000) }
        true
    }

    @MainThread
    fun stop() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        sound.stop()
    }
}
