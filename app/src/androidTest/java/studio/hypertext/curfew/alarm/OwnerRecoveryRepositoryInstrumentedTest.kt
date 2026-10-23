package studio.hypertext.curfew.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.persistence.AlarmRecoveryAuditEntity
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.platform.AlarmOwnerRecoveryRepository
import studio.hypertext.curfew.platform.AlarmRuntimeRepository

class OwnerRecoveryRepositoryInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = CurfewDatabase.open(context)
    private val runtime = AlarmRuntimeRepository(context)
    private val repository = AlarmOwnerRecoveryRepository(context)
    private val id = UUID.randomUUID().toString()
    private val reason = "My check-in is unavailable and I need to leave for work this morning."
    private val now = Instant.now()
    private val ringing = AlarmCampaignEngine().advanceTo(
        AlarmCampaignState.Scheduled(id, now, AlarmRecurrencePolicy.default(), setOf("fixture")), now,
    )

    private fun approval(): AuthorizedOwnerRecovery {
        var elapsed = 1_000L
        val authorization = OwnerRecoveryAuthorization { elapsed }
        authorization.beginHold(id, reason)
        elapsed += 3_000
        val request = requireNotNull(authorization.requestConfirmation())
        return requireNotNull(authorization.finishConfirmation(request.requestId, true))
    }

    @After
    fun cleanup() = runBlocking {
        database.alarmCampaignDao().delete(id)
        database.alarmRecoveryAuditDao().deleteForCampaign(id)
    }

    @Test
    fun confirmedRecoveryRecordsAnOverrideAndItsReasonAtomically() = runBlocking {
        runtime.save(ringing)
        val approved = approval()

        assertTrue(repository.recover(approved))

        val state = runtime.load(id) as AlarmCampaignState.Overridden
        assertEquals(approved.requestId, state.overrideId)
        val audit = database.alarmRecoveryAuditDao().forCampaign(id).single()
        assertEquals(reason, audit.reason)
        assertEquals(approved.requestId, audit.requestId)
        assertEquals(state.overriddenAt.toEpochMilli(), audit.confirmedAt)
        assertFalse(repository.recover(approved))
        assertEquals(1, database.alarmRecoveryAuditDao().forCampaign(id).size)
    }

    @Test
    fun recoveryStillWorksAfterTheWallClockMovesBehindTheAlarmStart() = runBlocking {
        val originalStart = now.plusSeconds(3_600)
        val active = AlarmCampaignEngine().advanceTo(
            AlarmCampaignState.Scheduled(id, originalStart, AlarmRecurrencePolicy.default(), setOf("fixture")),
            originalStart,
        )
        runtime.save(active)

        assertTrue(repository.recover(approval()))

        val recovered = runtime.load(id) as AlarmCampaignState.Overridden
        assertEquals(originalStart, recovered.overriddenAt)
        val audit = database.alarmRecoveryAuditDao().forCampaign(id).single()
        assertTrue(audit.confirmedAt < originalStart.toEpochMilli())
    }

    @Test
    fun quietIntervalCanBeRecoveredWithoutCallingItVerifiedWaking() = runBlocking {
        runtime.save(AlarmCampaignEngine().advanceTo(ringing, now.plusSeconds(120)))
        assertTrue(repository.recover(approval()))
        assertTrue(runtime.load(id) is AlarmCampaignState.Overridden)
    }

    @Test
    fun aVerifiedWakeThatWinsTheRaceKeepsItsResult() = runBlocking {
        val released = AlarmCampaignEngine().satisfy(ringing, now, "Morning check-in")
        runtime.save(released)
        assertFalse(repository.recover(approval()))
        assertEquals(released, runtime.load(id))
        assertTrue(database.alarmRecoveryAuditDao().forCampaign(id).isEmpty())
    }

    @Test
    fun recoveryDoesNotStopAFutureAlarm() = runBlocking {
        val future = AlarmCampaignState.Scheduled(id, now.plusSeconds(60), AlarmRecurrencePolicy.default(), setOf("fixture"))
        runtime.save(future)
        assertFalse(repository.recover(approval()))
        assertEquals(future, runtime.load(id))
        assertTrue(database.alarmRecoveryAuditDao().forCampaign(id).isEmpty())
    }

    @Test
    fun auditStorageFailureDoesNotLeaveAnUnauditedOverride() = runBlocking {
        runtime.save(ringing)
        val approved = approval()
        database.alarmRecoveryAuditDao().insert(AlarmRecoveryAuditEntity(approved.requestId, id, "Existing fixture", 0))

        assertTrue(runCatching { repository.recover(approved) }.isFailure)

        assertEquals(ringing, runtime.load(id))
        assertEquals("Existing fixture", database.alarmRecoveryAuditDao().forCampaign(id).single().reason)
    }
}
