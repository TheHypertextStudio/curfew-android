package studio.hypertext.curfew.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.persistence.CurfewDatabase

class AlarmFixtureCleanupInstrumentedTest {
    @Test fun cleanupRefusesToStopAnUnrelatedCampaign() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AlarmRuntimeRepository(context)
        check(repository.pending().isEmpty())
        val other = UUID.randomUUID().toString()
        val missingFixture = UUID.randomUUID().toString()
        val dao = CurfewDatabase.open(context).alarmCampaignDao()
        try {
            repository.save(AlarmCampaignState.Scheduled(other, Instant.now().plusSeconds(3600), AlarmRecurrencePolicy.default(), setOf("fixture")))
            var refused = false
            try {
                cleanupAlarmFixture(context, missingFixture)
            } catch (_: IllegalStateException) {
                refused = true
            }
            assertTrue("Fixture cleanup must refuse app-wide shutdown around another campaign", refused)
            assertNotNull(dao.find(other))
        } finally {
            dao.delete(other)
        }
    }
}
