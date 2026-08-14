package studio.hypertext.curfew.sync

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.persistence.SyncOutboxEntity
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.protocols.EncryptedRecordNamespace
import studio.hypertext.curfew.protocols.WakeOutcome
import studio.hypertext.curfew.security.AccountKeyMaterialStore
import studio.hypertext.curfew.security.AndroidDeviceSigner
import studio.hypertext.curfew.security.EncryptedRecordSealer
import studio.hypertext.curfew.security.canonicalJson

class WakeOutboxPublisher(context: Context) {
    private val appContext = context.applicationContext

    suspend fun publish(
        outcome: WakeOutcome,
        writerDeviceId: String,
        version: Long,
        keyEpoch: Long = 1,
    ): Boolean = withContext(Dispatchers.IO) {
        val rootKey = AccountKeyMaterialStore(appContext).accountRootKey()
            ?: return@withContext false
        val recordId = UUID.randomUUID().toString()
        val writerCounter = CurfewPreferences(appContext).nextWriterCounter()
        val fields = buildList<Pair<String, Any>> {
            outcome.attemptsCompleted?.let { add("attemptsCompleted" to it) }
            add("campaignId" to outcome.campaignId)
            add("releasedAt" to outcome.releasedAt)
            add("result" to outcome.result.value)
            outcome.satisfyingDeviceId?.let { add("satisfyingDeviceId" to it) }
        }
        val record = EncryptedRecordSealer(AndroidDeviceSigner()).seal(
            plaintextCanonical = canonicalJson(*fields.toTypedArray()).toByteArray(),
            rootKey = rootKey,
            namespace = EncryptedRecordNamespace.Campaigns,
            keyEpoch = keyEpoch,
            recordId = recordId,
            updatedAt = java.time.Instant.parse(outcome.releasedAt),
            version = version,
                writerCounter = writerCounter,
            writerDeviceId = writerDeviceId,
        )
        CurfewDatabase.open(appContext).syncOutboxDao().enqueue(
            SyncOutboxEntity(
                operationId = recordId,
                namespace = EncryptedRecordNamespace.Campaigns.value,
                encryptedPayload = Json.encodeToString(record).toByteArray(),
                createdAt = System.currentTimeMillis(),
                attemptCount = 0,
            ),
        )
        true
    }
}
