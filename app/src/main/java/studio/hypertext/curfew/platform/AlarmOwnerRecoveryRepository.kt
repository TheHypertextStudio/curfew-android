package studio.hypertext.curfew.platform

import android.content.Context
import androidx.room.withTransaction
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AuthorizedOwnerRecovery
import studio.hypertext.curfew.alarm.campaignStartedAt
import studio.hypertext.curfew.persistence.AlarmRecoveryAuditEntity
import studio.hypertext.curfew.persistence.CurfewDatabase

class AlarmOwnerRecoveryRepository(context: Context) {
    private val database = CurfewDatabase.open(context)
    private val runtime = AlarmRuntimeRepository(context)

    suspend fun recover(approval: AuthorizedOwnerRecovery): Boolean = withContext(Dispatchers.IO) {
        database.withTransaction {
            val current = runtime.load(approval.campaignId)
            if (current !is AlarmCampaignState.Ringing && current !is AlarmCampaignState.Quiet) {
                return@withTransaction false
            }
            val now = Instant.now()
            // A clock correction must not block local credential recovery. Keep the
            // engine observation valid while recording the actual wall time in the audit.
            val observedAt = maxOf(now, current.campaignStartedAt())
            val overridden = AlarmCampaignEngine().override(current, observedAt, approval.requestId, observedAt)
            val committed = runtime.save(overridden)
            if (committed != overridden) return@withTransaction false
            // Publish Room invalidation only after both the outcome and its reason commit.
            database.alarmRecoveryAuditDao().insert(AlarmRecoveryAuditEntity(
                approval.requestId, approval.campaignId, approval.reason, now.toEpochMilli(),
            ))
            true
        }
    }
}
