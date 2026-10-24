package studio.hypertext.curfew.sync

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.protocols.AuthorizedBy
import studio.hypertext.curfew.protocols.OverrideStatus
import studio.hypertext.curfew.protocols.RemoteOverride
import studio.hypertext.curfew.protocols.WakeCampaignState
import studio.hypertext.curfew.protocols.WakeStatus

class WakeConvergenceReducerTest {
    @Test
    fun `a fresh satisfied status releases without a deadline comparison`() {
        val local = AlarmCampaignState.Scheduled(
            "campaign",
            Instant.parse("2026-08-10T07:00:00Z"),
            AlarmRecurrencePolicy.default(),
            setOf("device"),
        )
        val remote = WakeStatus(
            attemptNumber = 0,
            campaignId = "campaign",
            selectedDeviceIds = listOf("device"),
            state = WakeCampaignState.Satisfied,
            statusVersion = 1,
            updatedAt = "2026-08-10T07:00:01Z",
        )

        assertTrue(WakeConvergenceReducer().apply(local, remote, 0).releasesWakeGate)
    }

    private val observedNow = Instant.parse("2026-10-23T07:00:00Z")
    private val reducer = WakeConvergenceReducer(clock = Clock.fixed(observedNow, ZoneOffset.UTC))
    private val pending = AlarmCampaignState.Scheduled(
        "campaign", observedNow.minusSeconds(3_600),
        AlarmRecurrencePolicy.default(), setOf("device"),
    )

    private fun grant(startsAt: Instant, status: OverrideStatus = OverrideStatus.Active) = RemoteOverride(
        authorizedBy = AuthorizedBy.FreshWebAal2,
        durationMinutes = 5,
        overrideId = "bounded-release",
        reason = "Owner recovery",
        requestId = "request",
        startsAt = startsAt.toString(),
        status = status,
        targetDeviceIds = listOf("device"),
    )

    @Test
    fun `expired active override cannot release an occurrence`() {
        val result = runCatching {
            reducer.applyOverride(pending, grant(observedNow.minusSeconds(300)), 1)
        }
        assertTrue("Expired grants must leave the alarm pending", result.isFailure)
    }

    @Test
    fun `future active override cannot release an occurrence`() {
        val result = runCatching {
            reducer.applyOverride(pending, grant(observedNow.plusSeconds(300)), 1)
        }
        assertTrue("Future grants must leave the alarm pending", result.isFailure)
    }

    @Test
    fun `current bounded override releases only its declared devices`() {
        val result = reducer.applyOverride(pending, grant(observedNow.minusSeconds(30)), 1)
        assertTrue(result.releasesWakeGate)
        assertTrue(result.devicesToRelease == setOf("device"))
    }

    @Test
    fun `cancelled override supplies no release`() {
        val result = runCatching {
            reducer.applyOverride(pending, grant(observedNow.minusSeconds(30), OverrideStatus.Cancelled), 1)
        }
        assertTrue(result.isFailure)
    }
}
