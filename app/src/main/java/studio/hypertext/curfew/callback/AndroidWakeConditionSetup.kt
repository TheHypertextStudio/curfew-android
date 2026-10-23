package studio.hypertext.curfew.callback

import android.content.Context
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.protocols.CallbackDefinition

class AndroidWakeConditionSetup(context: Context) {
    private val preferences = CurfewPreferences(context.applicationContext)
    private val configurations = CallbackConfigurationRepository(context.applicationContext)
    private val controller = WakeConditionSetupController(object : WakeConditionSetupStore {
        override suspend fun selected(): CallbackDefinition? =
            preferences.state.first().selectedCallbackId?.let { configurations.find(it) }

        override suspend fun save(label: String, endpoint: String, actionUrl: String?) =
            configurations.save(label, endpoint, actionUrl)

        override suspend fun selectUnverified(callbackId: String) = preferences.selectCallback(callbackId)

        override suspend fun markVerifiedIfSelected(callbackId: String) =
            preferences.markCallbackVerifiedIfSelected(callbackId)
    }) { definition ->
        // A setup challenge is never a saved alarm occurrence and cannot release one.
        val now = Instant.now()
        HttpsCallbackClient(InMemoryNonceLedger()).poll(
            definition, UUID.randomUUID().toString(), now, now,
        )
    }

    suspend fun save(label: String, endpoint: String, actionUrl: String?) =
        withContext(Dispatchers.IO) { controller.save(label, endpoint, actionUrl) }

    suspend fun provision(callbackId: String) = withContext(Dispatchers.IO) { controller.provision(callbackId) }

    suspend fun verify(callbackId: String) = withContext(Dispatchers.IO) { controller.verify(callbackId) }
}
