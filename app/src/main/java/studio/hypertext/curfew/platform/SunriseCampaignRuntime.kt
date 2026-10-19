package studio.hypertext.curfew.platform

import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.alarm.SunriseOccurrence
import studio.hypertext.curfew.alarm.SunriseOccurrencePhase
import studio.hypertext.curfew.alarm.SunriseScheduleRuntime

interface SunriseCampaignStore {
    suspend fun load(campaignId: String): AlarmCampaignState?
    suspend fun callbackId(campaignId: String): String?
    suspend fun putScheduled(state: AlarmCampaignState.Scheduled, callbackId: String)
    suspend fun removeScheduled(campaignId: String)
}

class SunriseCampaignRuntime(
    private val campaigns: SunriseCampaignStore,
    private val gateway: AlarmClockGateway,
    private val deviceId: String,
    private val releaseReady: suspend (String) -> Boolean,
) : SunriseScheduleRuntime {
    override suspend fun canArm(callbackId: String): Boolean =
        gateway.canScheduleExactAlarms() && releaseReady(callbackId)

    override suspend fun phase(campaignId: String): SunriseOccurrencePhase? =
        when (campaigns.load(campaignId)) {
            null -> null
            is AlarmCampaignState.Scheduled -> SunriseOccurrencePhase.SCHEDULED
            is AlarmCampaignState.Ringing, is AlarmCampaignState.Quiet -> SunriseOccurrencePhase.ACTIVE
            is AlarmCampaignState.Satisfied, is AlarmCampaignState.Overridden -> SunriseOccurrencePhase.FINISHED
        }

    override suspend fun arm(occurrence: SunriseOccurrence, callbackId: String) {
        check(gateway.canScheduleExactAlarms()) { "exact alarm permission is unavailable" }
        val previous = campaigns.load(occurrence.campaignId)
        check(previous == null || previous is AlarmCampaignState.Scheduled) {
            "an active or completed occurrence cannot be replaced"
        }
        val previousCallback = previous?.let {
            requireNotNull(campaigns.callbackId(it.campaignId)) { "existing alarm has no release condition" }
        }
        val scheduled = AlarmCampaignState.Scheduled(
            campaignId = occurrence.campaignId,
            startsAt = occurrence.startsAt,
            policy = previous?.policy ?: AlarmRecurrencePolicy.default(),
            selectedDeviceIds = previous?.selectedDeviceIds ?: setOf(deviceId),
        )
        val changed = previous != scheduled || previousCallback != callbackId
        if (changed) campaigns.putScheduled(scheduled, callbackId)
        try {
            gateway.setAlarmClock(occurrence.campaignId, occurrence.startsAt)
        } catch (failure: Exception) {
            if (changed) {
                runCatching {
                    if (previous is AlarmCampaignState.Scheduled) {
                        campaigns.putScheduled(previous, requireNotNull(previousCallback))
                    } else {
                        campaigns.removeScheduled(occurrence.campaignId)
                    }
                }.onFailure(failure::addSuppressed)
            }
            throw failure
        }
    }

    override suspend fun cancel(campaignId: String) {
        if (campaigns.load(campaignId) !is AlarmCampaignState.Scheduled) return
        gateway.cancel(campaignId)
        campaigns.removeScheduled(campaignId)
    }
}
