package studio.hypertext.curfew.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.account.AndroidAccountRecovery

class WakeSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = CurfewDatabase.open(applicationContext).syncOutboxDao()
        runCatching {
            AndroidAccountRecovery(applicationContext).distributeRootKeyToPeers()
        }
        val operations = dao.oldest(20)
        if (operations.isEmpty()) return@withContext Result.success()
        val accessToken = AndroidKeystoreSecretStore(applicationContext)
            .get("account:access-token")
            ?.toString(Charsets.UTF_8)
            ?: return@withContext Result.retry()
        val proofAuthenticator = NativeDeviceProofAuthenticator(applicationContext)
        for (operation in operations) {
            val delivered = runCatching {
                val endpoint = URI(
                    "https://curfew-sync.hypertext.studio/sync/records",
                )
                val jsonBody = operation.encryptedPayload.toString(Charsets.UTF_8)
                val connection = endpoint.toURL().openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    connection.doOutput = true
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
                    connection.responseCode in 200..299
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(false)
            if (delivered) dao.acknowledge(operation.operationId) else dao.recordFailure(operation.operationId)
        }
        if (operations.any { it.attemptCount >= 10 }) Result.failure() else Result.retry()
    }
}

object WakeSyncScheduler {
    private const val PERIODIC_SYNC = "curfew-periodic-sync"
    private const val ACCELERATED_SYNC = "curfew-accelerated-sync"
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun install(context: Context) {
        val request = PeriodicWorkRequestBuilder<WakeSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(15))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun accelerate(context: Context) {
        val request = OneTimeWorkRequestBuilder<WakeSyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(15))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ACCELERATED_SYNC,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
