package studio.hypertext.curfew.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OwnerRecoveryAuthorizationTest {
    private var elapsed = 1_000L
    private val authorization = OwnerRecoveryAuthorization { elapsed }
    private var requestId = "no-request"
    private val reason = "The check-in service is unavailable and I need to leave for work."

    @Test
    fun `a short reason cannot start recovery`() {
        assertThrows(IllegalArgumentException::class.java) {
            authorization.beginHold("morning", "Unavailable")
        }
        assertNull(authorization.requestConfirmation())
    }

    @Test
    fun `a partial hold cannot request authentication`() {
        authorization.beginHold("morning", reason)
        elapsed += 2_999
        assertNull(authorization.requestConfirmation())
        assertNull(authorization.finishConfirmation(requestId, true))
    }

    @Test
    fun `a cancelled hold cannot request authentication`() {
        authorization.beginHold("morning", reason)
        elapsed += 3_000
        authorization.cancelHold()
        assertNull(authorization.requestConfirmation())
    }

    @Test
    fun `a full hold still requires successful credential confirmation`() {
        authorization.beginHold("morning", reason)
        elapsed += 3_000
        val request = request()
        assertEquals("morning", request.campaignId)
        assertNull(authorization.finishConfirmation(requestId, false))
        assertNull(authorization.finishConfirmation(requestId, true))
    }

    @Test
    fun `authentication without a held request has no authority`() {
        assertNull(authorization.finishConfirmation(requestId, true))
    }

    @Test
    fun `confirmation is bound to the requested alarm and reason and is single use`() {
        authorization.beginHold("morning", "  $reason  ")
        elapsed += 3_000
        val request = request()
        val confirmed = requireNotNull(authorization.finishConfirmation(requestId, true))
        assertEquals(request.requestId, confirmed.requestId)
        assertEquals("morning", confirmed.campaignId)
        assertEquals(reason, confirmed.reason)
        assertNull(authorization.finishConfirmation(requestId, true))
    }

    @Test
    fun `an expired credential request cannot release an alarm`() {
        authorization.beginHold("morning", reason)
        elapsed += 3_000
        request()
        elapsed += 300_001
        assertNull(authorization.finishConfirmation(requestId, true))
    }

    @Test
    fun `an elapsed clock reset invalidates authorization`() {
        authorization.beginHold("morning", reason)
        elapsed += 3_000
        request()
        elapsed = 0
        assertNull(authorization.finishConfirmation(requestId, true))
    }

    @Test
    fun `starting another hold invalidates the earlier credential request`() {
        authorization.beginHold("morning", reason)
        elapsed += 3_000
        request()
        authorization.beginHold("another-morning", reason)
        assertNull(authorization.finishConfirmation(requestId, true))
    }
    private fun request() = requireNotNull(authorization.requestConfirmation()).also {
        requestId = it.requestId
    }

    @Test
    fun `an old prompt result cannot authorize a newer request`() {
        authorization.beginHold("morning", reason)
        elapsed += 3_000
        val old = request()
        authorization.beginHold("another-morning", reason)
        elapsed += 3_000
        val current = request()
        assertNull(authorization.finishConfirmation(old.requestId, true))
        assertEquals("another-morning", authorization.finishConfirmation(current.requestId, true)?.campaignId)
    }
}
