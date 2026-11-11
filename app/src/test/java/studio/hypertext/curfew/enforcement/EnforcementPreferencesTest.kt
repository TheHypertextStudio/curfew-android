package studio.hypertext.curfew.enforcement

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EnforcementPreferencesTest {

    @get:Rule
    val directory = TemporaryFolder()

    private val session = EnforcementSession(
        sessionId = "session-test-1",
        purpose = EnforcementPurpose.BEDTIME,
        startEpochMs = 1_700_000_000_000L,
        elapsedRealtimeStartMs = 120_000L,
        durationMinutes = 45,
        wallClockExpiryEpochMs = 1_700_000_000_000L + (45 * 60 * 1000L),
        chosenAppPackages = setOf("com.zhiliaoapp.musically", "com.instagram.android"),
        consentRevision = 2,
        state = EnforcementSessionState.ACTIVE,
    )

    private suspend fun <T> withStore(file: File, block: suspend (EnforcementPreferences) -> T): T {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
            produceFile = { file },
        )
        return block(EnforcementPreferences(dataStore))
    }

    @Test
    fun `fresh storage has no active session`() = runBlocking {
        withStore(File(directory.root, "empty.preferences_pb")) { store ->
            assertNull(store.readSession())
            assertEquals(0, store.readConsentRevision())
            assertEquals(emptySet<String>(), store.readChosenApps())
        }
    }

    @Test
    fun `saving session preserves all fields`() = runBlocking {
        val file = File(directory.root, "session.preferences_pb")
        withStore(file) { store ->
            store.saveSession(session)
            val loaded = store.readSession()
            assertNotNull(loaded)
            assertEquals("session-test-1", loaded?.sessionId)
            assertEquals(EnforcementPurpose.BEDTIME, loaded?.purpose)
            assertEquals(1_700_000_000_000L, loaded?.startEpochMs)
            assertEquals(45, loaded?.durationMinutes)
            assertEquals(setOf("com.zhiliaoapp.musically", "com.instagram.android"), loaded?.chosenAppPackages)
            assertEquals(EnforcementSessionState.ACTIVE, loaded?.state)
        }
    }

    @Test
    fun `emergency stop immediately cancels active session`() = runBlocking {
        val file = File(directory.root, "emergency.preferences_pb")
        withStore(file) { store ->
            store.saveSession(session)
            store.emergencyStop("User requested emergency stop")
            val loaded = store.readSession()
            assertNotNull(loaded)
            assertEquals(EnforcementSessionState.CANCELLED_EMERGENCY, loaded?.state)
        }
    }

    @Test
    fun `disarmOnReboot transitions active session to disarmed`() = runBlocking {
        val file = File(directory.root, "reboot.preferences_pb")
        withStore(file) { store ->
            store.saveSession(session)
            store.disarmOnReboot()
            val loaded = store.readSession()
            assertNotNull(loaded)
            assertEquals(EnforcementSessionState.DISARMED_AFTER_REBOOT, loaded?.state)
        }
    }

    @Test
    fun `stores consent revision and chosen apps independently`() = runBlocking {
        val file = File(directory.root, "config.preferences_pb")
        withStore(file) { store ->
            store.setConsentRevision(3)
            store.setChosenApps(setOf("app.one", "app.two"))

            assertEquals(3, store.readConsentRevision())
            assertEquals(setOf("app.one", "app.two"), store.readChosenApps())
        }
    }
}
