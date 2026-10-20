package studio.hypertext.curfew.alarm

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalAlarmTransitionTest {
    @Test(timeout = 5_000) fun theBroadcastFinishesBeforePublicationStarts() = runBlocking {
        val events = mutableListOf<String>()
        LocalAlarmTransition(
            applyLocal = { events += "local" },
            finishLocal = { events += "finished" },
            publish = { events += "publish" },
            onPublicationFailure = { fail("Publication should not fail") },
        ).deliver()
        assertEquals(listOf("local", "finished", "publish"), events)
    }

    @Test(timeout = 5_000) fun anUnavailableCoordinatorCannotPreventTheLocalAlarm() = runBlocking {
        val events = mutableListOf<String>()
        LocalAlarmTransition(
            applyLocal = { events += "local" },
            publish = { throw IOException("offline") },
            onPublicationFailure = { events += "publication-failed" },
        ).deliver()
        assertEquals(listOf("local", "publication-failed"), events)
    }

    @Test(timeout = 5_000) fun aSlowCoordinatorDoesNotDelayLocalDelivery() = runBlocking {
        val localDelivered = CompletableDeferred<Unit>()
        val publicationEntered = CompletableDeferred<Unit>()
        val releasePublication = CompletableDeferred<Unit>()
        val delivery = launch {
            LocalAlarmTransition(
                applyLocal = { localDelivered.complete(Unit); Unit },
                publish = { publicationEntered.complete(Unit); releasePublication.await() },
                onPublicationFailure = { fail("Publication should not fail") },
            ).deliver()
        }
        try {
            publicationEntered.await()
            assertTrue("Local playback must not wait for the coordinator", localDelivered.isCompleted)
        } finally {
            releasePublication.complete(Unit)
            delivery.join()
        }
    }

    @Test(timeout = 5_000) fun aLocalFailureCannotBeReportedAsApplied() = runBlocking {
        var published = false
        var finished = false
        val result = runCatching {
            LocalAlarmTransition(
                applyLocal = { throw IOException("local schedule unavailable") },
                finishLocal = { finished = true },
                publish = { published = true },
                onPublicationFailure = { fail("This is a local failure") },
            ).deliver()
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertFalse(published)
        assertTrue(finished)
    }

    @Test(timeout = 5_000) fun cancellationRemainsCancellation() = runBlocking {
        var reported = false
        val result = runCatching {
            LocalAlarmTransition(
                applyLocal = {},
                publish = { throw CancellationException("cancelled") },
                onPublicationFailure = { reported = true },
            ).deliver()
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(reported)
    }
}
