package studio.hypertext.curfew.alarm

import java.time.Instant

class AlarmPlaybackPlanner {
    fun restore(
        campaignId: String?,
        snapshots: List<AlarmRuntimeSnapshot>,
        nowWall: Instant,
        nowElapsedMillis: Long,
        currentBootId: String,
    ): AlarmCampaignState? {
        val snapshot = if (campaignId != null) {
            snapshots.find { it.state.campaignId == campaignId }
        } else {
            snapshots.filter {
                it.state is AlarmCampaignState.Ringing || it.state is AlarmCampaignState.Quiet
            }.maxByOrNull { it.persistedAtWall }
        } ?: return null
        return AlarmRecoveryPlanner(AlarmCampaignEngine()).recover(
            snapshot, nowWall, nowElapsedMillis, currentBootId,
        )
    }
}
