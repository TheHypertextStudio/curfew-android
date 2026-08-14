package studio.hypertext.curfew.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeScheduleResolverTest {
    private val resolver = WakeScheduleResolver()
    private val losAngeles = ZoneId.of("America/Los_Angeles")

    @Test
    fun `DST gap resolves to the first valid instant`() {
        val resolved = resolver.resolve(
            date = LocalDate.of(2026, 3, 8),
            time = LocalTime.of(2, 30),
            zoneId = losAngeles,
        )

        assertEquals(Instant.parse("2026-03-08T10:00:00Z"), resolved.toInstant())
        assertEquals(LocalTime.of(3, 0), resolved.toLocalTime())
    }

    @Test
    fun `DST overlap uses the first occurrence`() {
        val resolved = resolver.resolve(
            date = LocalDate.of(2026, 11, 1),
            time = LocalTime.of(1, 30),
            zoneId = losAngeles,
        )

        assertEquals(Instant.parse("2026-11-01T08:30:00Z"), resolved.toInstant())
    }
}
