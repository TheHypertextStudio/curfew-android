package studio.hypertext.curfew.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.persistence.CurfewDatabase

class AlarmRuntimeRepositoryInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = AlarmRuntimeRepository(context)
    private val id = UUID.randomUUID().toString()
    private val now = Instant.parse("2026-10-20T14:00:00Z")
    private val ringing = AlarmCampaignEngine().advanceTo(
        AlarmCampaignState.Scheduled(id, now, AlarmRecurrencePolicy.default(), setOf("fixture")), now,
    )

    @After
    fun removeFixture() = runBlocking { CurfewDatabase.open(context).alarmCampaignDao().delete(id) }

    @Test
    fun aLatePollCannotReopenAReleasedOccurrence() = runBlocking {
        repository.save(ringing, callbackId = "fixture-condition")
        val released = AlarmCampaignEngine().satisfy(ringing, now.plusSeconds(1), "Verified wake")
        repository.save(released)
        repository.save(ringing)
        assertEquals(released, repository.load(id))
        assertEquals("fixture-condition", repository.callbackId(id))
    }

    @Test
    fun aLateApprovalCannotReplaceAnAlreadyRecordedOverride() = runBlocking {
        repository.save(ringing)
        val overridden = AlarmCampaignEngine().override(ringing, now.plusSeconds(1), "owner-recovery", now.plusSeconds(60))
        repository.save(overridden)
        repository.save(AlarmCampaignEngine().satisfy(ringing, now.plusSeconds(2), "Late approval"))
        assertEquals(overridden, repository.load(id))
    }
    @Test
    fun aLateQuietSnapshotCannotMovePlaybackBackToThePreviousAttempt() = runBlocking {
        val engine = AlarmCampaignEngine()
        val quiet = engine.advanceTo(ringing, now.plusSeconds(120))
        val nextRing = engine.advanceTo(ringing, now.plusSeconds(180))
        repository.save(nextRing)
        repository.save(quiet)
        assertEquals(nextRing, repository.load(id))
    }

}
