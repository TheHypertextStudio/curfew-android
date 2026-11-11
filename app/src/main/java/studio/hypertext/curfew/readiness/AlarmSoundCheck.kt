package studio.hypertext.curfew.readiness

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stages of an alarm-sound rehearsal. Playback alone is not confirmation, and a failed
 * preference write leaves the user able to retry.
 */
enum class AlarmSoundCheckState { CLOSED, PLAYING, AWAITING_CONFIRMATION, FAILED, SAVING, SAVE_FAILED }

/**
 * Runs bounded preview and durable sound confirmation. Closing stops audio and invalidates a
 * late playback result; confirmation is reported only after the save callback returns.
 */
class AlarmSoundCheck(
    private val play: suspend () -> Boolean,
    private val stop: () -> Unit,
    private val saveConfirmation: suspend () -> Unit,
) : AutoCloseable {
    private val mutableState = MutableStateFlow(AlarmSoundCheckState.CLOSED)
    val state = mutableState.asStateFlow()
    private var generation = 0L

    suspend fun play() {
        if (mutableState.value == AlarmSoundCheckState.SAVING) return
        val current = ++generation
        stop()
        mutableState.value = AlarmSoundCheckState.PLAYING
        try {
            val started = play.invoke()
            if (current == generation) mutableState.value = if (started) AlarmSoundCheckState.AWAITING_CONFIRMATION else AlarmSoundCheckState.FAILED
        } catch (cancelled: CancellationException) {
            if (current == generation) close()
            throw cancelled
        } catch (_: Exception) {
            if (current == generation) {
                stop()
                mutableState.value = AlarmSoundCheckState.FAILED
            }
        }
    }

    suspend fun confirm(): Boolean {
        if (mutableState.value != AlarmSoundCheckState.AWAITING_CONFIRMATION && mutableState.value != AlarmSoundCheckState.SAVE_FAILED) return false
        val current = generation
        mutableState.value = AlarmSoundCheckState.SAVING
        stop()
        return try {
            saveConfirmation()
            if (current == generation) mutableState.value = AlarmSoundCheckState.CLOSED
            true
        } catch (cancelled: CancellationException) {
            if (current == generation) close()
            throw cancelled
        } catch (_: Exception) {
            if (current == generation) mutableState.value = AlarmSoundCheckState.SAVE_FAILED
            false
        }
    }

    override fun close() {
        generation++
        stop()
        mutableState.value = AlarmSoundCheckState.CLOSED
    }
}
