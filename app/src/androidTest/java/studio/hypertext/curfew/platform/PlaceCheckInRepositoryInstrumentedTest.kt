package studio.hypertext.curfew.platform

import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.checkin.PlaceCheckInRepository
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore

class PlaceCheckInRepositoryInstrumentedTest {
    @Test fun enrollmentAndRevocationPersistWithoutAService(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val places = PlaceCheckInRepository(context)
        val prefs = CurfewPreferences(context)
        val previous = prefs.state.first()
        assertNull("Do not replace an existing code draft", places.draft())
        val draft = places.begin("Fixture bathroom")
        try {
            assertEquals(draft, PlaceCheckInRepository(context).draft())
            try { places.enroll(draft.enrollment.id, "not-your-code"); fail("Wrong scan enrolled") }
            catch (_: IllegalArgumentException) { }
            assertEquals(previous.selectedCallbackId, prefs.state.first().selectedCallbackId)
            val saved = places.enroll(draft.enrollment.id, draft.payload)
            assertEquals(saved, PlaceCheckInRepository(context).find(saved.id))
            assertTrue(prefs.state.first().isCallbackReady(saved.id))
            assertNull(CurfewDatabase.open(context).callbackDefinitionDao().find(saved.id))
            places.remove(saved.id)
            assertNull(places.find(saved.id))
            assertFalse(prefs.state.first().isCallbackReady(saved.id))
        } finally {
            places.cancelDraft()
            places.remove(draft.enrollment.id)
            previous.selectedCallbackId?.let { prefs.selectCallback(it); if (previous.verifiedCallbackId == it) prefs.markCallbackVerifiedIfSelected(it) }
        }
    }

    @Test fun codeSatisfiesOnlyItsBoundOccurrenceAndDuplicateIsHarmless(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNull("This local fixture must not use an enrolled live account", AndroidKeystoreSecretStore(context).get("account:access-token"))
        val runtime = AlarmRuntimeRepository(context)
        assertTrue("Do not mutate a device with an active alarm", runtime.pending().isEmpty())
        val places = PlaceCheckInRepository(context)
        val prefs = CurfewPreferences(context)
        val previous = prefs.state.first()
        assertNull("Do not replace an existing code draft", places.draft())
        val draft = places.begin("Fixture kitchen")
        val id = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        try {
            places.enroll(draft.enrollment.id, draft.payload)
            val now = Instant.now()
            val state = AlarmCampaignState.Ringing(id, 1, now.minusSeconds(5), now.plusSeconds(115), AlarmRecurrencePolicy.default(), setOf("fixture-phone"))
            runtime.save(state, callbackId = draft.enrollment.id)
            runtime.save(state.copy(campaignId = other), callbackId = "another-condition")
            try { places.approve(other, draft.payload); fail("Released another condition") }
            catch (_: IllegalArgumentException) { }
            assertTrue(runtime.load(other) is AlarmCampaignState.Ringing)
            places.approve(id, draft.payload)
            val terminal = runtime.load(id)
            assertTrue(terminal is AlarmCampaignState.Satisfied)
            places.approve(id, draft.payload)
            assertEquals(terminal, runtime.load(id))
        } finally {
            CurfewDatabase.open(context).alarmCampaignDao().delete(id)
            CurfewDatabase.open(context).alarmCampaignDao().delete(other)
            places.remove(draft.enrollment.id)
            places.cancelDraft()
            previous.selectedCallbackId?.let { prefs.selectCallback(it); if (previous.verifiedCallbackId == it) prefs.markCallbackVerifiedIfSelected(it) }
        }
    }
}
