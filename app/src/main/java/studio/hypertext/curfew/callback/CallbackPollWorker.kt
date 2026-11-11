package studio.hypertext.curfew.callback

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.Operation
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.campaignStartedAt
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.SunriseScheduler
import studio.hypertext.curfew.platform.createAndroidPlaceCheckInRepository
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
    private val polling by lazy { applicationContext.callbackPollScheduler() }

    override suspend fun doWork(): Result {
        val campaignId = inputData.getString(KEY_CAMPAIGN_ID) ?: return Result.failure()
        val failureCount = inputData.getInt(KEY_FAILURE_COUNT, 0)
        val runtime = AlarmRuntimeRepository(applicationContext)
        val now = Instant.now()
        val state = runtime.recover(campaignId) ?: return Result.success()
        if (state !is AlarmCampaignState.Scheduled &&
            state !is AlarmCampaignState.Ringing &&
            state !is AlarmCampaignState.Quiet
        ) {
            return runCatching { SunriseScheduler(applicationContext).completeOccurrence(campaignId) }
                .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
        }
        val callbackId = runtime.callbackId(campaignId) ?: return Result.success()
        if (createAndroidPlaceCheckInRepository(applicationContext).find(callbackId) != null) {
            return Result.success() // Local camera approval never polls an HTTPS service.
        }
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
            polling.continuePolling(
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
            val committed = runtime.save(terminal)
            if (committed != terminal) return Result.success()
            val sunrise = SunriseScheduler(applicationContext)
            val completion = runCatching { sunrise.completeOccurrence(campaignId) }
                .onFailure { android.util.Log.w("SunriseRelease", "Next alarm needs attention") }
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
            WakeSyncScheduler.accelerate(applicationContext)
            return if (completion.isSuccess) Result.success() else Result.retry()
        }

        polling.continuePolling(
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

/**
 * Owns WorkManager requests for verifier polling and terminal alarm recovery.
 * Work is unique per campaign: a first request keeps existing work, and a
 * continuation appends after the worker that scheduled it.
 */
@Singleton
class CallbackPollScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager get() = WorkManager.getInstance(context)

    /** Schedule the first poll or a recovery attempt without replacing pending work. */
    fun schedule(
        campaignId: String,
        delay: Duration = Duration.ZERO,
        failureCount: Int = 0,
    ): Operation = enqueue(campaignId, delay, failureCount, ExistingWorkPolicy.KEEP)

    /** Append a poll after the currently running one, or replace a failed chain. */
    fun continuePolling(
        campaignId: String,
        delay: Duration,
        failureCount: Int = 0,
    ): Operation = enqueue(campaignId, delay, failureCount, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueue(
        campaignId: String,
        delay: Duration,
        failureCount: Int,
        policy: ExistingWorkPolicy,
    ): Operation {
        val input = Data.Builder()
            .putString(CallbackPollWorker.KEY_CAMPAIGN_ID, campaignId)
            .putInt(CallbackPollWorker.KEY_FAILURE_COUNT, failureCount)
            .build()
        val request = OneTimeWorkRequestBuilder<CallbackPollWorker>()
            .setInputData(input)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        return manager.enqueueUniqueWork(
            workName(campaignId),
            policy,
            request,
        )
    }

    /** Cancel this campaign's chain when it no longer needs a verifier. */
    fun cancel(campaignId: String) {
        manager.cancelUniqueWork(workName(campaignId))
    }

    private fun workName(campaignId: String) = "$WORK_NAME_PREFIX$campaignId"

    private companion object {
        const val WORK_NAME_PREFIX = "curfew-callback-"
    }
}

/** Gives framework-created workers access to the application-scoped polling scheduler. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CallbackPollDependencies {
    fun callbackPollScheduler(): CallbackPollScheduler
}

/** Resolves the shared scheduler at Android entry points that Hilt does not construct. */
internal fun Context.callbackPollScheduler(): CallbackPollScheduler =
    EntryPointAccessors.fromApplication(this, CallbackPollDependencies::class.java).callbackPollScheduler()
