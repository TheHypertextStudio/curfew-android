package studio.hypertext.curfew.callback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.protocols.CallbackDefinition
import studio.hypertext.curfew.protocols.CallbackPollPolicy

class WakeConditionSetupControllerTest {
    private val definition = CallbackDefinition(
        callbackId = "condition-1", displayLabel = "Start the day",
        endpoint = "https://verifier.example.test/wake",
        pollPolicy = CallbackPollPolicy(15, 60, 5),
        secret = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
    )
    private val store = MemoryStore()
    private var contacted: CallbackDefinition? = null
    private val controller = WakeConditionSetupController(store) { contacted = it }

    @Test
    fun `saving selects an unverified definition only after storage succeeds`() = runBlocking {
        assertEquals(definition, controller.save("Start the day", definition.endpoint, null))
        assertEquals(definition, store.current)
        assertNull(store.verified)
        assertNull(contacted)
    }

    @Test
    fun `failed storage does not select a condition or report success`() = runBlocking {
        store.failSave = true
        assertTrue(runCatching { controller.save("Start the day", definition.endpoint, null) }.isFailure)
        assertNull(store.current)
        assertNull(store.verified)
    }

    @Test
    fun `failed selection does not report a saved usable condition`() = runBlocking {
        store.failSelect = true
        assertTrue(runCatching { controller.save("Start the day", definition.endpoint, null) }.isFailure)
        assertNull(store.current)
    }

    @Test
    fun `a successful authenticated test marks only its selected condition verified`() = runBlocking {
        store.current = definition
        assertTrue(controller.verify(definition.callbackId))
        assertEquals(definition, contacted)
        assertEquals(definition.callbackId, store.verified)
    }

    @Test
    fun `unavailable or rejected verifier never marks the condition verified`() = runBlocking {
        store.current = definition
        val rejecting = WakeConditionSetupController(store) { error("No authenticated response") }
        assertTrue(runCatching { rejecting.verify(definition.callbackId) }.isFailure)
        assertNull(store.verified)
    }

    @Test
    fun `test completion cannot verify a replacement selected while the request was in flight`() = runBlocking {
        store.current = definition
        val response = CompletableDeferred<Unit>()
        val delayed = WakeConditionSetupController(store) { response.await() }
        val verification = async(start = CoroutineStart.UNDISPATCHED) { delayed.verify(definition.callbackId) }
        store.current = definition.copy(callbackId = "replacement")
        response.complete(Unit)
        assertFalse(verification.await())
        assertNull(store.verified)
    }

    @Test
    fun `stale condition test cannot contact or reselect an old configuration`() = runBlocking {
        store.current = definition.copy(callbackId = "replacement")
        assertFalse(controller.verify(definition.callbackId))
        assertNull(contacted)
        assertEquals("replacement", store.current?.callbackId)
    }

    @Test
    fun `provisioning returns the selected definition with its actual stored key`() = runBlocking {
        store.current = definition
        assertEquals(definition, controller.provision(definition.callbackId))
        assertNull(store.verified)
        assertNull(contacted)
    }

    @Test
    fun `provisioning cannot export a stale selection`() = runBlocking {
        store.current = definition.copy(callbackId = "replacement")
        assertTrue(runCatching { controller.provision(definition.callbackId) }.isFailure)
    }

    private inner class MemoryStore : WakeConditionSetupStore {
        var current: CallbackDefinition? = null
        var verified: String? = null
        var failSave = false
        var failSelect = false
        override suspend fun selected() = current
        override suspend fun save(label: String, endpoint: String, actionUrl: String?): CallbackDefinition {
            check(!failSave)
            return definition.copy(displayLabel = label, endpoint = endpoint, actionUrl = actionUrl)
        }
        override suspend fun selectUnverified(callbackId: String) {
            check(!failSelect)
            current = definition.copy(callbackId = callbackId)
            verified = null
        }
        override suspend fun markVerifiedIfSelected(callbackId: String): Boolean {
            if (current?.callbackId != callbackId) return false
            verified = callbackId
            return true
        }
    }
}
