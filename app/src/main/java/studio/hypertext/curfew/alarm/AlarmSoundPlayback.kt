package studio.hypertext.curfew.alarm

import kotlinx.coroutines.CancellationException

internal interface AlarmSoundSource {
    val isPlaying: Boolean
    fun play()
    fun stop()
}

/** The owner confines this playback session to one dispatcher. */
internal class AlarmSoundPlayback(
    private val preferred: suspend () -> AlarmSoundSource?,
    private val fallback: suspend () -> AlarmSoundSource?,
    private val awaitPlaying: suspend (AlarmSoundSource) -> Boolean,
) {
    private var active: AlarmSoundSource? = null
    private var generation = 0
    val isPlaying: Boolean get() = active?.isPlaying == true

    suspend fun play(): Boolean {
        stop()
        val request = generation
        for (load in listOf(preferred, fallback)) {
            if (request != generation) return false
            var candidate: AlarmSoundSource? = null
            var retained = false
            try {
                candidate = load() ?: continue
                if (request != generation) return false
                active = candidate
                candidate.play()
                if (awaitPlaying(candidate) && request == generation) {
                    retained = true
                    return true
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                // A missing provider, decoder, or file must not prevent the bundled attempt.
            } finally {
                if (!retained) {
                    candidate?.stop()
                    if (active === candidate) active = null
                }
            }
            if (request != generation) return false
        }
        return false
    }

    fun stop() {
        generation++
        active?.stop()
        active = null
    }
}
