package studio.hypertext.curfew.callback

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.campaignStartedAt
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway
import studio.hypertext.curfew.platform.PerpetualAlarmService
import studio.hypertext.curfew.protocols.ReceiptStatus
import studio.hypertext.curfew.protocols.Result as ProtocolResult
import studio.hypertext.curfew.protocols.WakeOutcome as ProtocolWakeOutcome
import studio.hypertext.curfew.sync.WakeOutboxPublisher
import studio.hypertext.curfew.sync.WakeSyncScheduler
import studio.hypertext.curfew.sync.WakeStatusPublisher

class CallbackPollWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val campaignId = inputData.getString(KEY_CAMPAIGN_ID) ?: return Result.failure()
        val failureCount = inputData.getInt(KEY_FAILURE_COUNT, 0)
        val runtime = AlarmRuntimeRepository(applicationContext)
        val persisted = runtime.load(campaignId) ?: return Result.success()
        val now = Instant.now()
        val state = AlarmCampaignEngine().advanceTo(persisted, now)
        runtime.save(state)
        if (state !is AlarmCampaignState.Scheduled &&
            state !is AlarmCampaignState.Ringing &&
            state !is AlarmCampaignState.Quiet
        ) {
            return Result.success()
        }
        val callbackId = runtime.callbackId(campaignId) ?: return Result.success()
        val definition = CallbackConfigurationRepository(applicationContext).find(callbackId)
            ?: return Result.failure()
        val acceptance = runCatching {
            HttpsCallbackClient(InMemoryNonceLedger()).poll(
                definition = definition,
                campaignId = campaignId,
                campaignStartedAt = state.campaignStartedAt(),
                now = now,
            )
        }.getOrElse {
            CallbackPollScheduler.schedule(
                applicationContext,
                campaignId,
                CallbackBackoff(definition.pollPolicy).afterFailure(failureCount),
                failureCount = (failureCount + 1).coerceAtMost(30),
            )
            return Result.success()
        }

        if (acceptance.receipt.status == ReceiptStatus.Satisfied) {
            val observedAt = Instant.parse(acceptance.receipt.observedAt)
            val terminal = AlarmCampaignEngine().satisfy(
                state,
                observedAt,
                definition.displayLabel,
            )
            runtime.save(terminal)
            WakeStatusPublisher(applicationContext).publish(terminal, observedAt)
            val deviceId = NativeDeviceProofAuthenticator(applicationContext).deviceId()
            WakeOutboxPublisher(applicationContext).publish(
                ProtocolWakeOutcome(
                    campaignId = campaignId,
                    releasedAt = observedAt.toString(),
                    result = ProtocolResult.Satisfied,
                    satisfyingDeviceId = deviceId,
                ),
                writerDeviceId = deviceId,
                version = 1,
            )
            applicationContext.stopService(
                PerpetualAlarmService.stopIntent(applicationContext, campaignId),
            )
            AndroidAlarmClockGateway(applicationContext).cancel(campaignId)
            WakeSyncScheduler.accelerate(applicationContext)
            return Result.success()
        }

        CallbackPollScheduler.schedule(
            applicationContext,
            campaignId,
            CallbackBackoff(definition.pollPolicy).afterSuccess(),
            failureCount = 0,
        )
        return Result.success()
    }

    companion object {
        const val KEY_CAMPAIGN_ID = "campaign_id"
        const val KEY_FAILURE_COUNT = "failure_count"
    }
}

object CallbackPollScheduler {
    fun schedule(
        context: Context,
        campaignId: String,
        delay: Duration = Duration.ZERO,
        failureCount: Int = 0,
    ) {
        val input = Data.Builder()
            .putString(CallbackPollWorker.KEY_CAMPAIGN_ID, campaignId)
            .putInt(CallbackPollWorker.KEY_FAILURE_COUNT, failureCount)
            .build()
        val request = OneTimeWorkRequestBuilder<CallbackPollWorker>()
            .setInputData(input)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(campaignId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context, campaignId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(campaignId))
    }

    private fun workName(campaignId: String) = "curfew-callback-$campaignId"
}
