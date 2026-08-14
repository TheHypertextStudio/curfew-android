package studio.hypertext.curfew.sync

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTransportPlanTest {
    @Test
    fun `plain distribution remains correct without push services`() {
        val plan = SyncTransportPlan.forDistribution(Distribution.PLAIN)

        assertFalse(plan.usesPushAcceleration)
        assertTrue(plan.usesForegroundWebSocket)
        assertTrue(plan.usesWorkManagerOutbox)
        assertEquals(Duration.ofSeconds(60), plan.maximumActivePollingInterval)
    }

    @Test
    fun `gms only adds acceleration to the same correctness transports`() {
        val plain = SyncTransportPlan.forDistribution(Distribution.PLAIN)
        val gms = SyncTransportPlan.forDistribution(Distribution.GMS)

        assertTrue(gms.usesPushAcceleration)
        assertEquals(plain.usesForegroundWebSocket, gms.usesForegroundWebSocket)
        assertEquals(plain.usesWorkManagerOutbox, gms.usesWorkManagerOutbox)
        assertEquals(plain.maximumActivePollingInterval, gms.maximumActivePollingInterval)
    }
}
