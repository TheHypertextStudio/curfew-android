package studio.hypertext.curfew.checkin

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy

class PlaceCheckInTest {
    private val now = Instant.parse("2026-10-23T07:00:10Z")
    private val ringing = AlarmCampaignState.Ringing("morning", 1, now.minusSeconds(10), now.plusSeconds(110), AlarmRecurrencePolicy.default(), setOf("phone"))

    @Test fun `scan enrolls a freshly generated code without a service`() {
        val draft = PlaceCheckIn.create("Bathroom")
        assertEquals("Bathroom", PlaceCheckIn.enroll(draft, draft.payload).label)
        assertNotEquals(draft.payload, draft.enrollment.digest)
        assertNotEquals(draft.payload, PlaceCheckIn.create("Bathroom").payload)
    }

    @Test fun `matching camera scan releases the current occurrence`() {
        val draft = PlaceCheckIn.create("Bathroom")
        val result = PlaceCheckIn.approve(draft.enrollment, draft.payload, "morning", ringing, now)
        assertTrue(result is AlarmCampaignState.Satisfied)
        assertEquals("morning", result.campaignId)
    }

    @Test fun `quiet intervals can be checked in without waiting for audio`() {
        val draft = PlaceCheckIn.create("Kitchen")
        val quiet = AlarmCampaignState.Quiet("morning", 1, now.plusSeconds(50), ringing.policy, ringing.selectedDeviceIds)
        assertTrue(PlaceCheckIn.approve(draft.enrollment, draft.payload, "morning", quiet, now) is AlarmCampaignState.Satisfied)
    }

    @Test fun `another code cannot enroll or release`() {
        val a = PlaceCheckIn.create("Bathroom")
        val b = PlaceCheckIn.create("Kitchen")
        assertThrows(IllegalArgumentException::class.java) { PlaceCheckIn.enroll(a, b.payload) }
        assertThrows(IllegalArgumentException::class.java) { PlaceCheckIn.approve(a.enrollment, b.payload, "morning", ringing, now) }
    }

    @Test fun `revoked or replaced code supplies no approval`() {
        val old = PlaceCheckIn.create("Bathroom")
        val replacement = PlaceCheckIn.create("Bathroom")
        assertThrows(IllegalArgumentException::class.java) { PlaceCheckIn.approve(null, old.payload, "morning", ringing, now) }
        assertThrows(IllegalArgumentException::class.java) { PlaceCheckIn.approve(replacement.enrollment, old.payload, "morning", ringing, now) }
    }

    @Test fun `late scanner result cannot release another morning`() {
        val draft = PlaceCheckIn.create("Bathroom")
        assertThrows(IllegalArgumentException::class.java) { PlaceCheckIn.approve(draft.enrollment, draft.payload, "yesterday", ringing, now) }
    }

    @Test fun `future or completed occurrences cannot be scanned to success`() {
        val draft = PlaceCheckIn.create("Bathroom")
        val future = AlarmCampaignState.Scheduled("morning", now.plusSeconds(60), ringing.policy, ringing.selectedDeviceIds)
        assertThrows(IllegalArgumentException::class.java) { PlaceCheckIn.approve(draft.enrollment, draft.payload, "morning", future, now) }
        val completed = AlarmCampaignState.Satisfied("morning", now, "Bathroom", devicesToRelease = ringing.selectedDeviceIds, policy = ringing.policy, selectedDeviceIds = ringing.selectedDeviceIds)
        assertThrows(IllegalArgumentException::class.java) { PlaceCheckIn.approve(draft.enrollment, draft.payload, "morning", completed, now) }
    }

    @Test fun `malformed and oversized codes never approve`() {
        val draft = PlaceCheckIn.create("Bathroom")
        for (payload in listOf("https://example.org", "", "x".repeat(10000), draft.payload + " ")) {
            assertThrows(IllegalArgumentException::class.java) { PlaceCheckIn.approve(draft.enrollment, payload, "morning", ringing, now) }
        }
    }
    @Test fun `corrupt persisted enrollment cannot appear ready`() {
        val valid = PlaceCheckIn.create("Bathroom").enrollment
        assertThrows(IllegalArgumentException::class.java) { valid.copy(id = "corrupt") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(label = "") }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(digest = "") }
    }
}
