package studio.hypertext.curfew.callback

import android.content.Context
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.persistence.CallbackDefinitionEntity
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.protocols.CallbackDefinition
import studio.hypertext.curfew.protocols.CallbackPollPolicy
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore

class CallbackConfigurationRepository(context: Context) {
    private val dao = CurfewDatabase.open(context).callbackDefinitionDao()
    private val secrets = AndroidKeystoreSecretStore(context)

    suspend fun save(
        displayLabel: String,
        endpoint: String,
        actionUrl: String?,
        callbackId: String = UUID.randomUUID().toString(),
    ): CallbackDefinition = withContext(Dispatchers.IO) {
        require(displayLabel.isNotBlank())
        val endpointUri = java.net.URI(endpoint)
        require(endpointUri.scheme == "https" && endpointUri.host != null)
        actionUrl?.takeIf(String::isNotBlank)?.let { java.net.URI(it) }
        val secret = ByteArray(32).also(SecureRandom()::nextBytes)
        secrets.put(secretIdentifier(callbackId), secret)
        val entity = CallbackDefinitionEntity(
            callbackId = callbackId,
            displayLabel = displayLabel,
            endpoint = endpoint,
            actionUrl = actionUrl?.takeIf(String::isNotBlank),
            intervalSeconds = 15,
            requestTimeoutSeconds = 5,
            maximumBackoffSeconds = 60,
        )
        dao.upsert(entity)
        entity.toProtocol(secret)
    }

    suspend fun find(callbackId: String): CallbackDefinition? = withContext(Dispatchers.IO) {
        val entity = dao.find(callbackId) ?: return@withContext null
        val secret = secrets.get(secretIdentifier(callbackId)) ?: return@withContext null
        entity.toProtocol(secret)
    }

    suspend fun delete(callbackId: String) = withContext(Dispatchers.IO) {
        dao.delete(callbackId)
        secrets.delete(secretIdentifier(callbackId))
    }

    private fun CallbackDefinitionEntity.toProtocol(secret: ByteArray) = CallbackDefinition(
        actionUrl = actionUrl,
        callbackId = callbackId,
        displayLabel = displayLabel,
        endpoint = endpoint,
        pollPolicy = CallbackPollPolicy(
            intervalSeconds = intervalSeconds,
            maximumBackoffSeconds = maximumBackoffSeconds,
            requestTimeoutSeconds = requestTimeoutSeconds,
        ),
        secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret),
    )

    private fun secretIdentifier(callbackId: String) = "callback:$callbackId"
}
