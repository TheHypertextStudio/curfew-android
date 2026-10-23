package studio.hypertext.curfew.alarm

import java.time.Instant

class AlarmPlaybackPlanner {
    fun restore(
        campaignId: String?,
        snapshots: List<AlarmRuntimeSnapshot>,
        nowWall: Instant,
        nowElapsedMillis: Long,
        currentBootId: String,
        previouslyRequestedIds: Set<String> = emptySet(),
        previouslyRequestedStickyRecovery: Boolean = false,
    ): AlarmCampaignState? {
        val snapshot = if (campaignId != null) {
            val requested = snapshots.find { it.state.campaignId == campaignId }
            if (requested == null || requested.state is AlarmCampaignState.Satisfied ||
                requested.state is AlarmCampaignState.Overridden
            ) {
                // A stale command may retain this service's alarm, but cannot select a new one.
                snapshots.filter {
                    (previouslyRequestedStickyRecovery || it.state.campaignId in previouslyRequestedIds) &&
                        (it.state is AlarmCampaignState.Ringing || it.state is AlarmCampaignState.Quiet)
                }.maxByOrNull { it.persistedAtWall } ?: requested
            } else requested
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
