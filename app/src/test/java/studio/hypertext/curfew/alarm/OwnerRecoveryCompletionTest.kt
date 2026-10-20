package studio.hypertext.curfew.alarm

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.yield
import kotlinx.coroutines.job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerRecoveryCompletionTest {
    private val approval = AuthorizedOwnerRecovery("request", "campaign", "My morning check-in is unavailable and I need to leave for work.")

    @Test
    fun recoveryCommitsBeforeConsumingTheMatchingOccurrence() = runBlocking {
        val calls = mutableListOf<String>()
        val completion = OwnerRecoveryCompletion(
            save = { calls += "saved:${it.campaignId}"; true },
            finishOccurrence = { calls += "consumed:$it" },
        )
        assertEquals(OwnerRecoveryResult.Recovered, completion.complete(approval))
        assertEquals(listOf("saved:campaign", "consumed:campaign"), calls)
    }

    @Test
    fun failedStorageDoesNotConsumeOrClaimRecovery() = runBlocking {
        var finished = false
        val completion = OwnerRecoveryCompletion(
            save = { throw IOException("disk unavailable") },
            finishOccurrence = { finished = true },
        )
        assertEquals(OwnerRecoveryResult.NotSaved, completion.complete(approval))
        assertFalse(finished)
    }

    @Test
    fun anAlreadyFinishedAlarmIsNotOverwrittenOrConsumed() = runBlocking {
        var finished = false
        val completion = OwnerRecoveryCompletion(save = { false }, finishOccurrence = { finished = true })
        assertEquals(OwnerRecoveryResult.NotActive, completion.complete(approval))
        assertFalse(finished)
    }

    @Test
    fun schedulingTomorrowCannotUndoTodaysCommittedRecovery() = runBlocking {
        var saved = false
        val completion = OwnerRecoveryCompletion(
            save = { saved = true; true },
            finishOccurrence = { throw IOException("cannot schedule tomorrow") },
        )
        assertEquals(OwnerRecoveryResult.RecoveredNextAlarmUnavailable, completion.complete(approval))
        assertTrue(saved)
    }

    @Test
    fun closingTheScreenAfterConfirmationDoesNotAbandonCommittedCleanup() = runBlocking {
        var finished = false
        val job = launch {
            val activityJob = currentCoroutineContext().job
            OwnerRecoveryCompletion(
                save = { activityJob.cancel(); true },
                finishOccurrence = { yield(); finished = true },
            ).complete(approval)
        }
        job.join()
        assertTrue("A confirmed recovery must finish its local cleanup", finished)
    }

    @Test
    fun cancellationDoesNotTurnIntoAStorageFailure() = runBlocking {
        val completion = OwnerRecoveryCompletion(save = { throw CancellationException() }, finishOccurrence = {})
        assertTrue(runCatching { completion.complete(approval) }.exceptionOrNull() is CancellationException)
    }
}
