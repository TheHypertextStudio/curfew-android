package studio.hypertext.curfew.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import studio.hypertext.curfew.persistence.CurfewPreferences

/**
 * Platform port for playing and stopping a bounded alarm preview. A sound-check entry owns one
 * output instance.
 */
internal interface AlarmSoundOutput {
    suspend fun play(): Boolean
    fun stop()
}

/**
 * Owns sound rehearsal for the readiness entry. It uses the ViewModel scope for playback and
 * confirmation, and registers the controller with addCloseable so clearing the entry stops
 * audio.
 */
@HiltViewModel
internal class AlarmSoundCheckViewModel @Inject constructor(
    output: AlarmSoundOutput,
    preferences: CurfewPreferences,
) : ViewModel() {
    private val check = AlarmSoundCheck(output::play, output::stop) {
        preferences.setTestSoundConfirmed(true)
    }.also { addCloseable(it) }

    val state = check.state
    private var playback: Job? = null

    fun play() {
        playback?.cancel()
        playback = viewModelScope.launch { check.play() }
    }

    fun confirm() {
        viewModelScope.launch { check.confirm() }
    }

    fun cancel() {
        playback?.cancel()
        playback = null
        if (state.value != AlarmSoundCheckState.SAVING) check.close()
    }
}
