package studio.hypertext.curfew.alarm

import java.time.Duration
import java.time.Instant

object AlarmRuntimeClock {
    private fun AlarmCampaignState.isActive() = this is AlarmCampaignState.Ringing || this is AlarmCampaignState.Quiet

    fun effectiveNow(snapshot: AlarmRuntimeSnapshot, nowWall: Instant, nowElapsedMillis: Long, currentBootId: String): Instant =
        if (snapshot.state.isActive() && snapshot.bootId == currentBootId && nowElapsedMillis >= snapshot.persistedAtElapsedMillis) {
            snapshot.persistedAtWall.plusMillis(nowElapsedMillis - snapshot.persistedAtElapsedMillis)
        } else nowWall

    fun checkpoint(
        previous: AlarmRuntimeSnapshot?,
        state: AlarmCampaignState,
        nowWall: Instant,
        nowElapsedMillis: Long,
        currentBootId: String,
    ): AlarmRuntimeSnapshot {
        val anchor = if (previous != null && previous.state.campaignId == state.campaignId && state.isActive()) {
            effectiveNow(previous, nowWall, nowElapsedMillis, currentBootId)
        } else nowWall
        return AlarmRuntimeSnapshot(state, anchor, nowElapsedMillis, currentBootId)
    }

    fun nextTransitionAt(snapshot: AlarmRuntimeSnapshot, nowWall: Instant, nowElapsedMillis: Long, currentBootId: String): Instant? {
        val boundary = when (val state = snapshot.state) {
            is AlarmCampaignState.Scheduled -> return state.startsAt
            is AlarmCampaignState.Ringing -> state.ringEndsAt
            is AlarmCampaignState.Quiet -> state.nextAttemptAt
            is AlarmCampaignState.Satisfied, is AlarmCampaignState.Overridden -> return null
        }
        val effectiveNow = effectiveNow(snapshot, nowWall, nowElapsedMillis, currentBootId)
        return nowWall.plus(Duration.between(effectiveNow, boundary))
    }
}
