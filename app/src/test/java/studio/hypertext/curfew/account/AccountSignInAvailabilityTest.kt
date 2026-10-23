package studio.hypertext.curfew.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSignInAvailabilityTest {
    @Test fun normalApplicationCanStartAccountSignIn() {
        assertTrue(canUseAccountSignIn("studio.hypertext.curfew"))
    }
    @Test fun isolatedAndUnrecognizedApplicationsCannotStartProductionSignIn() {
        for (id in listOf("studio.hypertext.curfew.acceptance", "studio.hypertext.curfew.other", "")) {
            assertFalse(canUseAccountSignIn(id))
        }
    }
}
