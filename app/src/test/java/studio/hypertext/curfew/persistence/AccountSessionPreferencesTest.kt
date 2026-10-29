package studio.hypertext.curfew.persistence

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
import java.io.File

class AccountSessionPreferencesTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `legacy session never hides current native sign in and local data stays intact`() =
        runBlocking {
            withStore { store ->
                val signedIn = booleanPreferencesKey("account_signed_in")
                val enrolled = booleanPreferencesKey("account_enrolled")
                val selected = stringPreferencesKey("selected_callback")
                store.edit {
                    it[signedIn] = true
                    it[enrolled] = true
                    it[selected] = "existing-wake-condition"
                }
                val session = AccountSessionPreferences(store)
                assertFalse(session.state.first().signedIn)
                assertFalse(session.state.first().enrolled)
                assertTrue(store.data.first()[signedIn] == true)
                assertTrue(store.data.first()[enrolled] == true)
                assertEquals("existing-wake-condition", store.data.first()[selected])
            }
        }

    @Test
    fun `current issuer session survives reopening without restoring legacy authority`() =
        runBlocking {
            withStore {
                val session = AccountSessionPreferences(it)
                session.setSignedIn(true)
                session.setEnrolled(true)
            }
            withStore {
                val state = AccountSessionPreferences(it).state.first()
                assertTrue(state.signedIn)
                assertTrue(state.enrolled)
            }
        }

    @Test
    fun `recovery acknowledgement cannot enroll a signed out session`() =
        runBlocking {
            withStore {
                val session = AccountSessionPreferences(it)
                session.setEnrolled(true)
                assertFalse(session.state.first().enrolled)
                session.setSignedIn(true)
                assertFalse(session.state.first().enrolled)
                session.setEnrolled(true)
                assertTrue(session.state.first().enrolled)
                session.setSignedIn(true)
                assertFalse(session.state.first().enrolled)
                session.setSignedIn(false)
                assertFalse(session.state.first().signedIn)
                assertFalse(session.state.first().enrolled)
            }
        }

    @Test
    fun `failed recovery leaves native sign in available and background sync disarmed`() =
        runBlocking {
            withStore {
                val session = AccountSessionPreferences(it)
                session.setSignedIn(true)
                session.setEnrolled(true)
                val failure = runCatching { session.completeSignIn<Unit> { error("recovery unavailable") } }
                assertTrue(failure.isFailure)
                assertFalse(session.state.first().signedIn)
                assertFalse(session.state.first().enrolled)
                val recovered = session.completeSignIn { "restored" }
                assertEquals("restored", recovered)
                assertTrue(session.state.first().signedIn)
                assertFalse(session.state.first().enrolled)
            }
        }

    @Test
    fun `recreated session awaiting recovery keeps its native continue setup action`() =
        runBlocking {
            withStore { AccountSessionPreferences(it).setSignedIn(true) }
            withStore {
                val session = AccountSessionPreferences(it)
                val restored = session.state.first()
                assertTrue(restored.signedIn)
                assertFalse(restored.enrolled)
                assertTrue(restored.needsEnrollment)
                session.setEnrolled(true)
                assertFalse(session.state.first().needsEnrollment)
            }
        }

    private suspend fun withStore(
        block: suspend (androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(directory.root, "session.preferences_pb") }
            block(store)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
