package studio.hypertext.curfew.readiness

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AlarmSoundCheckState { CLOSED, PLAYING, AWAITING_CONFIRMATION, FAILED, SAVING, SAVE_FAILED }

class AlarmSoundCheck(
    private val play: suspend () -> Boolean,
    private val stop: () -> Unit,
    private val saveConfirmation: suspend () -> Unit,
) {
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

    fun close() {
        generation++
        stop()
        mutableState.value = AlarmSoundCheckState.CLOSED
    }
}
