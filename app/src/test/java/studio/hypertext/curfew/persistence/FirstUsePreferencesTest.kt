package studio.hypertext.curfew.persistence

import studio.hypertext.curfew.alarm.AlarmSetupDraft

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FirstUsePreferencesTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun `fresh install welcomes only when no alarm exists`() = runBlocking {
        withStore {
            assertTrue(it.state.first().needsWelcome(hasSavedAlarm = false))
            assertFalse(it.state.first().needsWelcome(hasSavedAlarm = true))
        }
    }

    @Test fun `continue survives restart without claiming alarm readiness`() = runBlocking {
        withStore { it.chooseContinue() }
        withStore {
            assertEquals(FirstUseChoice.STARTED, it.state.first().choice)
            assertFalse(it.state.first().needsWelcome(hasSavedAlarm = false))
        }
    }

    @Test fun `not now survives restart and continue can resume setup`() = runBlocking {
        withStore { it.chooseNotNow() }
        withStore {
            assertEquals(FirstUseChoice.DEFERRED, it.state.first().choice)
            assertFalse(it.state.first().needsWelcome(hasSavedAlarm = false))
            it.chooseContinue()
            assertEquals(FirstUseChoice.STARTED, it.state.first().choice)
        }
    }

    @Test fun `draft survives cold restart separately from activation`() = runBlocking {
        val draft = studio.hypertext.curfew.alarm.AlarmSetupDraft(
            "draft-1", java.time.LocalTime.of(7, 45), java.time.LocalDate.of(2026, 10, 22), emptySet(),
        )
        withStore { it.chooseContinue(); it.saveDraft(draft) }
        withStore {
            assertEquals(draft, it.state.first().draft)
            assertEquals(FirstUseChoice.STARTED, it.state.first().choice)
            it.clearDraft()
            assertEquals(null, it.state.first().draft)
            assertEquals(FirstUseChoice.STARTED, it.state.first().choice)
        }
    }

    @Test fun `weekly draft restores all selected days`() = runBlocking {
        val draft = studio.hypertext.curfew.alarm.AlarmSetupDraft(
            "draft-2", java.time.LocalTime.of(6, 15), null,
            setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.SUNDAY),
        )
        withStore { it.saveDraft(draft) }
        withStore { assertEquals(draft, it.state.first().draft) }
    }

    @Test fun `invalid persisted draft cannot become a ready alarm`() = runBlocking {
        withStore(seed = {
            it[stringPreferencesKey("setup_draft_id")] = "draft-broken"
            it[stringPreferencesKey("setup_draft_time")] = "not-a-time"
            it[stringPreferencesKey("setup_draft_days")] = "9"
        }) {
            assertEquals(null, it.state.first().draft)
            assertTrue(it.state.first().needsWelcome(hasSavedAlarm = false))
        }
    }

    private suspend fun withStore(seed: ((MutablePreferences) -> Unit)? = null, action: suspend (FirstUsePreferences) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val data = PreferenceDataStoreFactory.create(scope = scope) {
                File(directory.root, "first-use.preferences_pb")
            }
            if (seed != null) data.edit { seed(it) }
            action(FirstUsePreferences(data))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
