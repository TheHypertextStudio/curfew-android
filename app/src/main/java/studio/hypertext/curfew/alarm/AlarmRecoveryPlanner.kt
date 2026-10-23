package studio.hypertext.curfew.alarm

import java.time.Instant

data class AlarmRuntimeSnapshot(
    val state: AlarmCampaignState,
    val persistedAtWall: Instant,
    val persistedAtElapsedMillis: Long,
    val bootId: String,
)

class AlarmRecoveryPlanner(
    private val engine: AlarmCampaignEngine,
) {
    fun recover(
        snapshot: AlarmRuntimeSnapshot,
        nowWall: Instant,
        nowElapsedMillis: Long,
        currentBootId: String,
    ): AlarmCampaignState {
        val effectiveNow = AlarmRuntimeClock.effectiveNow(snapshot, nowWall, nowElapsedMillis, currentBootId)
        return engine.advanceTo(snapshot.state, effectiveNow)
    }
}
