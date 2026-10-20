package studio.hypertext.curfew.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.persistence.CurfewDatabase

class AlarmRecoveryConcurrencyInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = AlarmRuntimeRepository(context)
    private val dao = CurfewDatabase.open(context).alarmCampaignDao()
    private val id = "recovery-race-${UUID.randomUUID()}"
    private val scheduled = AlarmCampaignState.Scheduled(
        id, Instant.now().plusSeconds(3_600), AlarmRecurrencePolicy.default(), setOf("fixture"),
    )

    @After fun cleanUp() = runBlocking { dao.delete(id) }

    @Test(timeout = 10_000) fun disablingCannotBeUndoneByRecoveryAlreadyReadingTheOccurrence() = runBlocking {
        repository.save(scheduled, callbackId = "fixture-condition")
        val read = CompletableDeferred<Unit>()
        val continueRecovery = CompletableDeferred<Unit>()
        val recovery = async {
            repository.updateExisting(id) { current ->
                read.complete(Unit)
                continueRecovery.await()
                current.state
            }
        }
        read.await()
        val disable = async(start = CoroutineStart.UNDISPATCHED) { dao.deleteScheduled(id) }
        withTimeoutOrNull(250) { disable.await() }
        continueRecovery.complete(Unit)
        recovery.await()
        disable.await()
        assertNull("Recovery must not recreate a deleted future alarm", repository.load(id))
        assertNull(repository.updateExisting(id) { fail("A missing occurrence cannot be recovered"); it.state })
    }

    @Test(timeout = 10_000) fun aSameDayEditCannotBeOverwrittenByRecoveryAlreadyReadingTheOccurrence() = runBlocking {
        repository.save(scheduled, callbackId = "fixture-condition")
        val read = CompletableDeferred<Unit>()
        val continueRecovery = CompletableDeferred<Unit>()
        val recovery = async {
            repository.updateExisting(id) { current ->
                read.complete(Unit)
                continueRecovery.await()
                current.state
            }
        }
        read.await()
        val edited = scheduled.copy(startsAt = scheduled.startsAt.plusSeconds(900))
        val edit = async(start = CoroutineStart.UNDISPATCHED) { repository.save(edited) }
        withTimeoutOrNull(250) { edit.await() }
        continueRecovery.complete(Unit)
        recovery.await()
        edit.await()
        assertEquals(edited, repository.load(id))
        assertEquals("fixture-condition", repository.callbackId(id))
    }
}
