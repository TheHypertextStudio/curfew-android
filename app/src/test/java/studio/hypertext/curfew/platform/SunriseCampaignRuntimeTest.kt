package studio.hypertext.curfew.platform

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.alarm.SunriseOccurrence
import studio.hypertext.curfew.alarm.SunriseOccurrencePhase

class SunriseCampaignRuntimeTest {
    private val occurrence = SunriseOccurrence(
        "morning-1", LocalDate.of(2026, 10, 20), Instant.parse("2026-10-20T14:00:00Z"),
    )
    private val campaigns = MemoryCampaigns()
    private val gateway = AlarmGateway()
    private val runtime = SunriseCampaignRuntime(campaigns, gateway, "phone-1") { true }

    @Test
    fun `arming creates a persistent local campaign using the chosen callback`() = runBlocking {
        runtime.arm(occurrence, "condition-1")

        val state = campaigns.state as AlarmCampaignState.Scheduled
        assertEquals(setOf("phone-1"), state.selectedDeviceIds)
        assertEquals(occurrence.startsAt, state.startsAt)
        assertEquals(AlarmRecurrencePolicy.default(), state.policy)
        assertEquals("condition-1", campaigns.callback)
        assertEquals(occurrence.startsAt, gateway.trigger)
    }

    @Test
    fun `missing exact access does not leave a scheduled row`() = runBlocking {
        gateway.exact = false

        assertNotNull(runCatching { runtime.arm(occurrence, "condition-1") }.exceptionOrNull())

        assertNull(campaigns.state)
        assertNull(gateway.trigger)
    }

    @Test
    fun `failed scheduling restores the previous time and callback`() = runBlocking {
        val old = scheduled()
        campaigns.state = old
        campaigns.callback = "old-condition"
        gateway.fail = true

        assertNotNull(runCatching {
            runtime.arm(occurrence.copy(startsAt = occurrence.startsAt.plusSeconds(3_600)), "new-condition")
        }.exceptionOrNull())

        assertEquals(old, campaigns.state)
        assertEquals("old-condition", campaigns.callback)
    }

    @Test
    fun `a late schedule request cannot reset active ringing`() = runBlocking {
        val ringing = AlarmCampaignEngine().advanceTo(scheduled(), occurrence.startsAt)
        campaigns.state = ringing

        assertNotNull(runCatching { runtime.arm(occurrence, "condition-2") }.exceptionOrNull())

        assertEquals(ringing, campaigns.state)
    }

    @Test
    fun `future cancellation cannot remove an active alarm`() = runBlocking {
        val ringing = AlarmCampaignEngine().advanceTo(scheduled(), occurrence.startsAt)
        campaigns.state = ringing

        runtime.cancel(occurrence.campaignId)

        assertEquals(ringing, campaigns.state)
        assertEquals(0, gateway.cancellations)
        assertEquals(SunriseOccurrencePhase.ACTIVE, runtime.phase(occurrence.campaignId))
    }

    @Test
    fun `cancelling a future occurrence removes it from runtime recovery`() = runBlocking {
        campaigns.state = scheduled()

        runtime.cancel(occurrence.campaignId)

        assertNull(campaigns.state)
        assertEquals(1, gateway.cancellations)
    }

    @Test
    fun `terminal runtime is reported as finished and cannot be rearmed`() = runBlocking {
        campaigns.state = AlarmCampaignEngine().satisfy(scheduled(), occurrence.startsAt, "Start the day")

        assertEquals(SunriseOccurrencePhase.FINISHED, runtime.phase(occurrence.campaignId))
        assertNotNull(runCatching { runtime.arm(occurrence, "condition-1") }.exceptionOrNull())
    }

    private fun scheduled() = AlarmCampaignState.Scheduled(
        occurrence.campaignId, occurrence.startsAt, AlarmRecurrencePolicy.default(), setOf("phone-1"),
    )

    private class MemoryCampaigns : SunriseCampaignStore {
        var state: AlarmCampaignState? = null
        var callback: String? = null

        override suspend fun load(campaignId: String): AlarmCampaignState? =
            state?.takeIf { it.campaignId == campaignId }

        override suspend fun callbackId(campaignId: String): String? = callback

        override suspend fun putScheduled(state: AlarmCampaignState.Scheduled, callbackId: String) {
            this.state = state
            callback = callbackId
        }

        override suspend fun removeScheduled(campaignId: String) {
            if (state is AlarmCampaignState.Scheduled) state = null
        }
    }

    private class AlarmGateway : AlarmClockGateway {
        var exact = true
        var fail = false
        var trigger: Instant? = null
        var cancellations = 0

        override fun canScheduleExactAlarms(): Boolean = exact

        override fun setAlarmClock(campaignId: String, triggerAt: Instant) {
            check(!fail) { "alarm scheduling failed" }
            trigger = triggerAt
        }

        override fun cancel(campaignId: String) {
            cancellations += 1
            trigger = null
        }
    }
}
