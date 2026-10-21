package studio.hypertext.curfew.ui

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.persistence.FirstUsePreferences
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.SunriseScheduler

class SetupJournalFixtureInstrumentedTest {
    @Test fun rejectsPendingCampaignWithoutASavedOccurrence(): Unit = runBlocking {
        assumeTrue("Fixture safety runs on a disposable emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = AlarmRuntimeRepository(context)
        assertTrue("Fixture needs an empty runtime queue", runtime.pending().isEmpty())
        val scheduler = SunriseScheduler(context)
        val alarm = scheduler.state.first()
        assertNull("Fixture needs no saved occurrence", alarm?.occurrence)
        val preferences = CurfewPreferences(context).state.first()
        val introduction = FirstUsePreferences(context).state.first()
        val id = "setup-guard-${UUID.randomUUID()}"
        val now = Instant.now()
        val ringing = AlarmCampaignState.Ringing(id, 1, now, now.plusSeconds(120), AlarmRecurrencePolicy.default(), setOf("fixture-device"))
        val journals = SetupJournalFixture(context)
        runtime.save(ringing)
        try {
            var refused = false
            try { journals.isolate() } catch (_: AssertionError) { refused = true }
            assertTrue("The reset must refuse a pending campaign without a saved occurrence", refused)
            assertEquals(alarm, scheduler.state.first())
            assertEquals(preferences, CurfewPreferences(context).state.first())
            assertEquals(introduction, FirstUsePreferences(context).state.first())
            assertEquals(ringing, runtime.load(id))
        } finally {
            journals.restore()
            CurfewDatabase.open(context).alarmCampaignDao().delete(id)
        }
    }
}
