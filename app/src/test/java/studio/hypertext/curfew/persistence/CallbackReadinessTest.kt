package studio.hypertext.curfew.persistence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallbackReadinessTest {
    private val saved = CurfewPreferenceState(null, false, false, false, "saved", false)

    @Test
    fun `saving alone cannot make a release condition ready`() {
        assertFalse(saved.isCallbackReady("saved"))
    }

    @Test
    fun `verification applies only to the matching selected condition`() {
        val verified = saved.copy(verifiedCallbackId = "saved")
        assertTrue(verified.isCallbackReady("saved"))
        assertFalse(verified.isCallbackReady("other"))
        assertFalse(saved.copy(verifiedCallbackId = "old").isCallbackReady("saved"))
    }
}
