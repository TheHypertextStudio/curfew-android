package studio.hypertext.curfew.alarm

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AlarmSoundPlaybackTest {
    private class Source(val canPlay: Boolean = true) : AlarmSoundSource {
        var playing = false
        var stops = 0
        override fun play() { playing = canPlay }
        override fun stop() { playing = false; stops++ }
        override val isPlaying get() = playing
    }

    @Test fun aWorkingDefaultDoesNotStartTheFallback() = runBlocking {
        val preferred = Source()
        var fallbacks = 0
        val playback = AlarmSoundPlayback({ preferred }, { fallbacks++; Source() }) { it.isPlaying }
        assertTrue(playback.play())
        assertTrue(preferred.playing)
        assertEquals(0, fallbacks)
        playback.stop()
        assertFalse(preferred.playing)
    }

    @Test fun anUnreadableDefaultUsesTheFallback() = runBlocking {
        val fallback = Source()
        val playback = AlarmSoundPlayback({ throw IOException("missing sound") }, { fallback }) { it.isPlaying }
        assertTrue(playback.play())
        assertTrue(fallback.playing)
        playback.stop()
        assertFalse(fallback.playing)
    }

    @Test fun aSilentDefaultIsStoppedBeforeTheFallbackStarts() = runBlocking {
        val preferred = Source(false)
        val fallback = Source()
        val playback = AlarmSoundPlayback({ preferred }, {
            assertTrue("The failed sound must be released first", preferred.stops > 0)
            fallback
        }) { it.isPlaying }
        assertTrue(playback.play())
        assertTrue(fallback.playing)
    }

    @Test fun failureOfBothSoundsDoesNotClaimPlayback() = runBlocking {
        val fallback = Source(false)
        val playback = AlarmSoundPlayback({ null }, { fallback }) { it.isPlaying }
        assertFalse(playback.play())
        assertFalse(playback.isPlaying)
        assertTrue(fallback.stops > 0)
    }

    @Test fun stoppingDuringLoadingCannotResurrectAudio() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val loaded = CompletableDeferred<AlarmSoundSource?>()
        val late = Source()
        val playback = AlarmSoundPlayback({ entered.complete(Unit); loaded.await() }, { error("Unexpected fallback") }) { it.isPlaying }
        val result = async { playback.play() }
        entered.await()
        playback.stop()
        loaded.complete(late)
        assertFalse(result.await())
        assertFalse(late.playing)
        assertTrue(late.stops > 0)
    }

    @Test fun aMissingLateSourceCannotLoadTheFallbackAfterStop() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val loaded = CompletableDeferred<AlarmSoundSource?>()
        var fallbackLoads = 0
        val playback = AlarmSoundPlayback({ entered.complete(Unit); loaded.await() }, { fallbackLoads++; Source() }) { it.isPlaying }
        val result = async { playback.play() }
        entered.await()
        playback.stop()
        loaded.complete(null)
        assertFalse(result.await())
        assertEquals(0, fallbackLoads)
    }

    @Test fun aLateOlderLoadCannotStopTheNewPlayback() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val oldLoaded = CompletableDeferred<AlarmSoundSource?>()
        val oldSource = Source()
        val currentSource = Source()
        var loads = 0
        val playback = AlarmSoundPlayback({
            if (++loads == 1) { entered.complete(Unit); oldLoaded.await() } else currentSource
        }, { error("Unexpected fallback") }) { it.isPlaying }
        val oldResult = async { playback.play() }
        entered.await()
        assertTrue(playback.play())
        oldLoaded.complete(oldSource)
        assertFalse(oldResult.await())
        assertFalse(oldSource.playing)
        assertTrue(currentSource.playing)
        assertTrue(playback.isPlaying)
        playback.stop()
    }

    @Test fun stoppingDuringConfirmationCannotStartAFallback() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val confirmed = CompletableDeferred<Boolean>()
        val preferred = Source()
        val playback = AlarmSoundPlayback({ preferred }, { error("Unexpected fallback") }) {
            entered.complete(Unit)
            confirmed.await()
        }
        val result = async { playback.play() }
        entered.await()
        playback.stop()
        confirmed.complete(true)
        assertFalse(result.await())
        assertFalse(preferred.playing)
    }

    @Test fun cancellationStopsTheSoundWithoutStartingAFallback() = runBlocking {
        val preferred = Source()
        val playback = AlarmSoundPlayback({ preferred }, { error("Unexpected fallback") }) { throw CancellationException("cancelled") }
        val result = runCatching { playback.play() }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(preferred.playing)
        assertTrue(preferred.stops > 0)
    }
}
