package studio.hypertext.curfew.callback

import studio.hypertext.curfew.protocols.CallbackDefinition

interface WakeConditionSetupStore {
    suspend fun selected(): CallbackDefinition?
    suspend fun save(label: String, endpoint: String, actionUrl: String?): CallbackDefinition
    suspend fun selectUnverified(callbackId: String)
    suspend fun markVerifiedIfSelected(callbackId: String): Boolean
}

class WakeConditionSetupController(
    private val store: WakeConditionSetupStore,
    private val verifyConnection: suspend (CallbackDefinition) -> Unit,
) {
    suspend fun save(label: String, endpoint: String, actionUrl: String?): CallbackDefinition {
        val definition = store.save(label, endpoint, actionUrl)
        store.selectUnverified(definition.callbackId)
        return definition
    }

    suspend fun provision(callbackId: String): CallbackDefinition =
        requireNotNull(store.selected()?.takeIf { it.callbackId == callbackId }) {
            "The selected wake condition changed"
        }

    suspend fun verify(callbackId: String): Boolean {
        val definition = store.selected()?.takeIf { it.callbackId == callbackId } ?: return false
        verifyConnection(definition)
        // A response may arrive after the owner has selected a different condition.
        return store.markVerifiedIfSelected(callbackId)
    }
}
