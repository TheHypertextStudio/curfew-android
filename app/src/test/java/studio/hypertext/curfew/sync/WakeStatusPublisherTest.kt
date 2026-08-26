package studio.hypertext.curfew.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy

class WakeStatusPublisherTest {
    @Test
    fun `the wire status has no deadline or attempt ceiling`() {
        val status = WakeStatusFactory.create(
            AlarmCampaignState.Scheduled(
                "campaign",
                Instant.parse("2026-08-10T07:00:00Z"),
                AlarmRecurrencePolicy.default(),
                setOf("device"),
            ),
            1,
            Instant.parse("2026-08-10T06:00:00Z"),
        )

        assertEquals(0, status.attemptNumber)
        assertFalse(status.toString().contains("finalDeadlineAt"))
        assertFalse(status.toString().contains("maximumAttempts"))
    }
}
