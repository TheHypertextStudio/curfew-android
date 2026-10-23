package studio.hypertext.curfew.readiness

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class AlarmSoundCheckTest {
    @Test fun playbackNeverCountsAsHearing() = runBlocking {
        var confirmations = 0
        val check = AlarmSoundCheck(play = { true }, stop = {}, saveConfirmation = { confirmations++ })
        check.play()
        assertEquals(AlarmSoundCheckState.AWAITING_CONFIRMATION, check.state.value)
        assertEquals(0, confirmations)
        assertTrue(check.confirm())
        assertEquals(1, confirmations)
        assertEquals(AlarmSoundCheckState.CLOSED, check.state.value)
        assertFalse(check.confirm())
        assertEquals(1, confirmations)
    }

    @Test fun confirmationRequiresSuccessfulPlayback() = runBlocking {
        var confirmations = 0
        val check = AlarmSoundCheck(play = { false }, stop = {}, saveConfirmation = { confirmations++ })
        assertFalse(check.confirm())
        check.play()
        assertEquals(AlarmSoundCheckState.FAILED, check.state.value)
        assertFalse(check.confirm())
        assertEquals(0, confirmations)
    }

    @Test fun leavingDuringPlaybackCannotOfferStaleConfirmation() = runBlocking {
        val playing = CompletableDeferred<Boolean>()
        var stops = 0
        val check = AlarmSoundCheck(play = { playing.await() }, stop = { stops++ }, saveConfirmation = { error("Must not confirm") })
        val job = launch { check.play() }
        yield()
        check.close()
        playing.complete(true)
        job.join()
        assertEquals(AlarmSoundCheckState.CLOSED, check.state.value)
        assertFalse(check.confirm())
        assertTrue(stops > 0)
    }

    @Test fun replayStartsANewCheckWithoutSaving() = runBlocking {
        var plays = 0
        val check = AlarmSoundCheck(play = { ++plays == 1 }, stop = {}, saveConfirmation = { error("Must not confirm") })
        check.play()
        check.play()
        assertEquals(2, plays)
        assertEquals(AlarmSoundCheckState.FAILED, check.state.value)
        assertFalse(check.confirm())
    }

    @Test fun aStorageFailureDoesNotClaimCompletion() = runBlocking {
        val check = AlarmSoundCheck(play = { true }, stop = {}, saveConfirmation = { throw IOException("disk unavailable") })
        check.play()
        assertFalse(check.confirm())
        assertEquals(AlarmSoundCheckState.SAVE_FAILED, check.state.value)
    }

    @Test fun aPlaybackFailureCanBeRetried() = runBlocking {
        var fail = true
        val check = AlarmSoundCheck(play = { if (fail) throw IOException("unavailable") else true }, stop = {}, saveConfirmation = {})
        check.play()
        assertEquals(AlarmSoundCheckState.FAILED, check.state.value)
        fail = false
        check.play()
        assertTrue(check.confirm())
    }
}
