package studio.hypertext.curfew.platform

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import studio.hypertext.curfew.readiness.AlarmPresentation
import studio.hypertext.curfew.readiness.AlarmReadiness

class AlarmSchedulingCoordinatorTest {
    @Test
    fun `ready campaign is scheduled through the visible alarm clock path`() {
        val gateway = RecordingAlarmClockGateway(canSchedule = true)
        val coordinator = AlarmSchedulingCoordinator(gateway)

        coordinator.arm(
            campaignId = "campaign-1",
            triggerAt = Instant.parse("2026-08-10T14:00:00Z"),
            readiness = ready(),
        )

        assertEquals("campaign-1", gateway.scheduledCampaign)
        assertEquals(Instant.parse("2026-08-10T14:00:00Z"), gateway.scheduledAt)
    }

    @Test
    fun `permission revocation prevents arming instead of silently degrading`() {
        val coordinator = AlarmSchedulingCoordinator(
            RecordingAlarmClockGateway(canSchedule = false),
        )

        assertThrows(AlarmNotReadyException::class.java) {
            coordinator.arm(
                "campaign-1",
                Instant.parse("2026-08-10T14:00:00Z"),
                ready(),
            )
        }
    }

    @Test
    fun `permission grant boot package and time changes reschedule persisted work`() {
        val gateway = RecordingAlarmClockGateway(canSchedule = true)
        val coordinator = AlarmSchedulingCoordinator(gateway)

        RescheduleReason.entries.forEach { reason ->
            coordinator.restore(
                pending = listOf(
                    PendingAlarm("campaign-1", Instant.parse("2026-08-10T14:00:00Z")),
                ),
                reason = reason,
            )
        }

        assertEquals(RescheduleReason.entries.size, gateway.scheduleCount)
    }

    private fun ready() = AlarmReadiness(
        canArm = true,
        requiredActions = emptySet(),
        disclosures = emptySet(),
        presentation = AlarmPresentation.FULL_SCREEN,
    )
}

private class RecordingAlarmClockGateway(
    private val canSchedule: Boolean,
) : AlarmClockGateway {
    var scheduledCampaign: String? = null
    var scheduledAt: Instant? = null
    var scheduleCount = 0

    override fun canScheduleExactAlarms(): Boolean = canSchedule

    override fun setAlarmClock(campaignId: String, triggerAt: Instant) {
        scheduledCampaign = campaignId
        scheduledAt = triggerAt
        scheduleCount += 1
    }

    override fun cancel(campaignId: String) = Unit
}
