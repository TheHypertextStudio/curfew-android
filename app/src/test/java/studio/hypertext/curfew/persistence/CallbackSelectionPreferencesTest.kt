package studio.hypertext.curfew.persistence

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CallbackSelectionPreferencesTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `fresh installation has no verified release condition`() = runBlocking {
        withStore("fresh") { store ->
            assertNull(store.state.first().selectedCallbackId)
            assertFalse(store.state.first().ready)
        }
    }

    @Test
    fun `successful verification survives reopening the local store`() = runBlocking {
        withStore("verified") {
            it.select("condition-1")
            assertTrue(it.markVerifiedIfSelected("condition-1"))
        }
        withStore("verified") {
            assertTrue(it.state.first().ready)
            assertEquals("condition-1", it.state.first().verifiedCallbackId)
        }
    }

    @Test
    fun `selecting a replacement clears the previous verification`() = runBlocking {
        withStore("replacement") {
            it.select("old")
            it.markVerifiedIfSelected("old")
            it.select("new")
            assertEquals("new", it.state.first().selectedCallbackId)
            assertNull(it.state.first().verifiedCallbackId)
            assertFalse(it.state.first().ready)
        }
    }

    @Test
    fun `late verification cannot bless the current replacement`() = runBlocking {
        withStore("late") {
            it.select("new")
            assertFalse(it.markVerifiedIfSelected("old"))
            assertFalse(it.state.first().ready)
        }
    }

    @Test
    fun `condition changes preserve unrelated readiness and account preferences`() = runBlocking {
        val file = File(directory.root, "shared.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val data = PreferenceDataStoreFactory.create(scope = scope) { file }
            val unrelated = stringPreferencesKey("account_marker")
            data.edit { it[unrelated] = "preserve" }
            val store = CallbackSelectionPreferences(data)
            store.select("condition-1")
            store.markVerifiedIfSelected("condition-1")
            assertEquals("preserve", data.data.first()[unrelated])
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    private suspend fun withStore(name: String, action: suspend (CallbackSelectionPreferences) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val data = PreferenceDataStoreFactory.create(scope = scope) {
                File(directory.root, "$name.preferences_pb")
            }
            action(CallbackSelectionPreferences(data))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
