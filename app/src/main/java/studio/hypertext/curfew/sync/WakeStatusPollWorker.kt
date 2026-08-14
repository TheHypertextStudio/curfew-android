package studio.hypertext.curfew.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway
import studio.hypertext.curfew.platform.PerpetualAlarmService
import studio.hypertext.curfew.protocols.RemoteOverride
import studio.hypertext.curfew.protocols.WakeCampaignState
import studio.hypertext.curfew.protocols.WakeStatus
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator

class WakeStatusPollWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val campaignId = inputData.getString(KEY_CAMPAIGN_ID) ?: return Result.failure()
        val failureCount = inputData.getInt(KEY_FAILURE_COUNT, 0)
        val runtime = AlarmRuntimeRepository(applicationContext)
        val local = runtime.load(campaignId) ?: return Result.success()
        if (local !is AlarmCampaignState.Scheduled &&
            local !is AlarmCampaignState.Ringing &&
            local !is AlarmCampaignState.Quiet
        ) return Result.success()
        val accessToken = AndroidKeystoreSecretStore(applicationContext)
            .get("account:access-token")
            ?.toString(Charsets.UTF_8)
            ?: return Result.success()
        val proofAuthenticator = NativeDeviceProofAuthenticator(applicationContext)

        val activeOverride = runCatching {
            fetch<RemoteOverride>("remote-overrides/active", campaignId, accessToken)
        }.getOrNull()
        if (activeOverride != null && proofAuthenticator.deviceId() in activeOverride.targetDeviceIds) {
            val startsAt = Instant.parse(activeOverride.startsAt)
            val terminal = runCatching {
                AlarmCampaignEngine().override(
                    local,
                    startsAt,
                    activeOverride.overrideId,
                    startsAt.plusSeconds(activeOverride.durationMinutes * 60),
                )
            }.getOrNull()
            if (terminal != null) {
                val nextVersion = runtime.statusVersion(campaignId) + 1
                runtime.save(terminal, writerCounter = nextVersion)
                stopCampaign(campaignId)
                return Result.success()
            }
        }

        val status = runCatching {
            fetch<WakeStatus>("wake-status", campaignId, accessToken)
        }.getOrElse {
            WakeStatusPollScheduler.schedule(
                applicationContext,
                campaignId,
                retryDelay(failureCount),
                (failureCount + 1).coerceAtMost(30),
            )
            return Result.success()
        }

        val terminal = runCatching {
            when (status.state) {
                WakeCampaignState.Satisfied -> WakeConvergenceReducer().apply(
                    local,
                    status,
                    runtime.statusVersion(campaignId),
                ).let {
                    AlarmCampaignEngine().satisfy(
                        local,
                        Instant.parse(status.updatedAt),
                        "Selected device condition",
                    )
                }
                WakeCampaignState.Overridden -> {
                    val override = fetch<RemoteOverride>(
                        "remote-overrides/active",
                        campaignId,
                        accessToken,
                    )
                    val localDeviceId = proofAuthenticator.deviceId()
                    if (localDeviceId !in override.targetDeviceIds) return@runCatching null
                    WakeConvergenceReducer().applyOverride(
                        local,
                        override,
                        status.statusVersion,
                    )
                    val startsAt = Instant.parse(override.startsAt)
                    AlarmCampaignEngine().override(
                        local,
                        startsAt,
                        override.overrideId,
                        startsAt.plusSeconds(override.durationMinutes * 60),
                    )
                }
                WakeCampaignState.Exhausted -> AlarmCampaignEngine().advanceTo(
                    local,
                    Instant.parse(status.finalDeadlineAt),
                )
                WakeCampaignState.QuietInterval,
                WakeCampaignState.RingingAttempt,
                WakeCampaignState.Scheduled,
                -> null
            }
        }.getOrNull()

        if (terminal != null) {
            runtime.save(terminal, writerCounter = status.statusVersion)
            stopCampaign(campaignId)
            return Result.success()
        }

        WakeStatusPollScheduler.schedule(
            applicationContext,
            campaignId,
            Duration.ofSeconds(60),
            failureCount = 0,
        )
        return Result.success()
    }

    private fun stopCampaign(campaignId: String) {
        applicationContext.stopService(
            PerpetualAlarmService.stopIntent(applicationContext, campaignId),
        )
        AndroidAlarmClockGateway(applicationContext).cancel(campaignId)
        CallbackPollScheduler.cancel(applicationContext, campaignId)
    }

    private inline fun <reified T> fetch(
        resource: String,
        campaignId: String,
        accessToken: String,
    ): T {
        val encodedCampaign = URLEncoder.encode(campaignId, StandardCharsets.UTF_8)
        val path = when (resource) {
            "wake-status" -> "sync/wake/status"
            "remote-overrides/active" -> "sync/remote-overrides/active"
            else -> error("unsupported sync resource")
        }
        val endpoint = java.net.URI(
            "https://curfew-sync.hypertext.studio/$path?campaignId=$encodedCampaign",
        )
        val url = endpoint.toURL()
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            val proofAuthenticator = NativeDeviceProofAuthenticator(applicationContext)
            connection.setRequestProperty("X-Curfew-Device-ID", proofAuthenticator.deviceId())
            connection.setRequestProperty(
                "DPoP",
                proofAuthenticator.proof(
                    accessToken = accessToken,
                    method = "GET",
                    canonicalUrl = endpoint.toASCIIString(),
                ),
            )
            val status = connection.responseCode
            if (status in 300..399) error("sync redirects are rejected")
            if (status !in 200..299) error("sync returned HTTP $status")
            val body = connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                require(text.length <= 32 * 1024) { "sync response is too large" }
                text
            }
            Json.decodeFromString(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun retryDelay(failureCount: Int): Duration {
        val seconds = (15L shl failureCount.coerceIn(0, 2)).coerceAtMost(60L)
        return Duration.ofSeconds(seconds)
    }

    companion object {
        const val KEY_CAMPAIGN_ID = "campaign_id"
        const val KEY_FAILURE_COUNT = "failure_count"
    }
}

object WakeStatusPollScheduler {
    fun schedule(
        context: Context,
        campaignId: String,
        delay: Duration = Duration.ZERO,
        failureCount: Int = 0,
    ) {
        val request = OneTimeWorkRequestBuilder<WakeStatusPollWorker>()
            .setInputData(
                Data.Builder()
                    .putString(WakeStatusPollWorker.KEY_CAMPAIGN_ID, campaignId)
                    .putInt(WakeStatusPollWorker.KEY_FAILURE_COUNT, failureCount)
                    .build(),
            )
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "curfew-wake-status-$campaignId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
