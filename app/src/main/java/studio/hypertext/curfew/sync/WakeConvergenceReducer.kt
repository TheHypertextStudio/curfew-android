package studio.hypertext.curfew.sync

import java.time.Instant
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.campaignStartedAt
import studio.hypertext.curfew.protocols.OverrideStatus
import studio.hypertext.curfew.protocols.RemoteOverride
import studio.hypertext.curfew.protocols.Result
import studio.hypertext.curfew.protocols.WakeCampaignState
import studio.hypertext.curfew.protocols.WakeStatus

data class WakeConvergenceResult(
    val outcome: Result,
    val devicesToRelease: Set<String>,
    val releasesWakeGate: Boolean,
    val statusVersion: Long,
)

class WakeConvergenceReducer(
    private val engine: AlarmCampaignEngine = AlarmCampaignEngine(),
) {
    fun apply(
        local: AlarmCampaignState,
        remote: WakeStatus,
        lastStatusVersion: Long,
    ): WakeConvergenceResult {
        require(remote.campaignId == local.campaignId) { "campaign does not match" }
        require(remote.statusVersion > lastStatusVersion) { "status version is stale" }
        require(remote.selectedDeviceIds.toSet() == local.selectedDeviceIds) {
            "selected devices do not match"
        }
        require(remote.maximumAttempts.toInt() == local.policy.maximumAttempts)
        val campaignStart = local.campaignStartedAt()
        require(
            Instant.parse(remote.finalDeadlineAt) == local.policy.deadlineFrom(campaignStart),
        ) { "campaign deadline does not match" }
        require(remote.state == WakeCampaignState.Satisfied) {
            "only a satisfied remote campaign can release the wake gate"
        }
        val terminal = engine.satisfy(
            local,
            Instant.parse(remote.updatedAt),
            "Selected device condition",
        ) as AlarmCampaignState.Satisfied
        return WakeConvergenceResult(
            outcome = terminal.outcome,
            devicesToRelease = terminal.devicesToRelease,
            releasesWakeGate = terminal.releasesWakeGate,
            statusVersion = remote.statusVersion,
        )
    }

    fun applyOverride(
        local: AlarmCampaignState,
        override: RemoteOverride,
        statusVersion: Long,
    ): WakeConvergenceResult {
        require(override.status == OverrideStatus.Active) { "override is not active" }
        require(override.durationMinutes in 5..60) { "override duration is out of bounds" }
        require(override.reason.isNotBlank())
        require(override.targetDeviceIds.isNotEmpty())
        require(override.targetDeviceIds.all { it in local.selectedDeviceIds }) {
            "override targets an unselected device"
        }
        val startsAt = Instant.parse(override.startsAt)
        val terminal = engine.override(
            local,
            startsAt,
            override.overrideId,
            startsAt.plusSeconds(override.durationMinutes * 60),
        ) as AlarmCampaignState.Overridden
        return WakeConvergenceResult(
            outcome = terminal.outcome,
            devicesToRelease = override.targetDeviceIds.toSet(),
            releasesWakeGate = terminal.releasesWakeGate,
            statusVersion = statusVersion,
        )
    }
}
