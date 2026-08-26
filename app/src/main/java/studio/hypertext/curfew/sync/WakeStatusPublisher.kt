package studio.hypertext.curfew.sync

import android.content.Context
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.protocols.WakeCampaignState
import studio.hypertext.curfew.protocols.WakeStatus
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore

object WakeStatusFactory {
    fun create(
        state: AlarmCampaignState,
        statusVersion: Long,
        updatedAt: Instant,
    ): WakeStatus {
        require(statusVersion > 0)
        val campaignState = when (state) {
            is AlarmCampaignState.Scheduled -> WakeCampaignState.Scheduled
            is AlarmCampaignState.Ringing -> WakeCampaignState.RingingAttempt
            is AlarmCampaignState.Quiet -> WakeCampaignState.QuietInterval
            is AlarmCampaignState.Satisfied -> WakeCampaignState.Satisfied
            is AlarmCampaignState.Overridden -> WakeCampaignState.Overridden
        }
        val attemptNumber = when (state) {
            is AlarmCampaignState.Scheduled -> 0
            is AlarmCampaignState.Ringing -> state.attempt
            is AlarmCampaignState.Quiet -> state.completedAttempt
            is AlarmCampaignState.Satisfied,
            is AlarmCampaignState.Overridden,
            -> 0
        }
        return WakeStatus(
            attemptNumber = attemptNumber.toLong(),
            campaignId = state.campaignId,
            selectedDeviceIds = state.selectedDeviceIds.sorted(),
            state = campaignState,
            statusVersion = statusVersion,
            updatedAt = updatedAt.toString(),
        )
    }
}

class WakeStatusPublisher(context: Context) {
    private val appContext = context.applicationContext

    suspend fun publish(
        state: AlarmCampaignState,
        updatedAt: Instant = Instant.now(),
    ): Boolean = withContext(Dispatchers.IO) {
        val accessToken = AndroidKeystoreSecretStore(appContext)
            .get("account:access-token")
            ?.toString(Charsets.UTF_8)
            ?: return@withContext false
        val repository = AlarmRuntimeRepository(appContext)
        var nextVersion = repository.statusVersion(state.campaignId) + 1
        val endpoint = URI("https://curfew-sync.hypertext.studio/sync/wake/status")
        val proofAuthenticator = NativeDeviceProofAuthenticator(appContext)
        repeat(2) {
            val status = WakeStatusFactory.create(state, nextVersion, updatedAt)
            val jsonBody = Json.encodeToString(status)
            val connection = endpoint.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.doOutput = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("X-Curfew-Device-ID", proofAuthenticator.deviceId())
                connection.setRequestProperty(
                    "DPoP",
                    proofAuthenticator.proof(
                        accessToken = accessToken,
                        method = "POST",
                        canonicalUrl = endpoint.toASCIIString(),
                        jsonBody = jsonBody,
                    ),
                )
                connection.outputStream.use { it.write(jsonBody.toByteArray()) }
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    repository.save(state, writerCounter = nextVersion)
                    return@withContext true
                }
                if (responseCode in 300..399 || responseCode != 409) return@withContext false
                val errorBody = connection.errorStream?.bufferedReader()?.use { reader ->
                    reader.readText().take(32 * 1024)
                } ?: return@withContext false
                val current = runCatching {
                    Json.decodeFromJsonElement<WakeStatus>(
                        Json.parseToJsonElement(errorBody).jsonObject.getValue("current"),
                    )
                }.getOrNull() ?: return@withContext false
                if (
                    current.campaignId != state.campaignId ||
                    current.selectedDeviceIds.toSet() != state.selectedDeviceIds ||
                    current.state == WakeCampaignState.Satisfied ||
                    current.state == WakeCampaignState.Overridden
                ) return@withContext false
                nextVersion = current.statusVersion + 1
            } finally {
                connection.disconnect()
            }
        }
        false
    }
}
