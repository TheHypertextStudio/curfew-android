package studio.hypertext.curfew.sync

import java.time.Duration

enum class Distribution { PLAIN, GMS }

data class SyncTransportPlan(
    val usesPushAcceleration: Boolean,
    val usesForegroundWebSocket: Boolean = true,
    val usesWorkManagerOutbox: Boolean = true,
    val maximumActivePollingInterval: Duration = Duration.ofSeconds(60),
) {
    companion object {
        fun forDistribution(distribution: Distribution): SyncTransportPlan =
            SyncTransportPlan(usesPushAcceleration = distribution == Distribution.GMS)
    }
}
