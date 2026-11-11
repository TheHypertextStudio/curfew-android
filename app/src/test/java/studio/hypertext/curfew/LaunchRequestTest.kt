package studio.hypertext.curfew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LaunchRequestTest {
    @Test
    fun oauthCallbackWinsAndItsCodeIsNotLoggedByToString() {
        val url = "studio.hypertext.curfew://oauth/callback?code=fixture-code&state=fixture-state"
        val request = parseLaunchRequest(url, showAlarm = true, campaignId = "alarm-1") as LaunchRequest.OAuthCallback
        assertEquals(url, request.url)
        assertFalse(request.toString().contains("fixture-code"))
        assertFalse(request.toString().contains("fixture-state"))
    }

    @Test
    fun unrelatedAndMalformedUrisAreIgnored() {
        for (url in listOf(
            "https://oauth/callback",
            "studio.hypertext.curfew://elsewhere/callback",
            "studio.hypertext.curfew://oauth/callback/extra",
            "studio.hypertext.curfew://oauth/callback?code=%zz",
        )) {
            assertEquals(LaunchRequest.None, parseLaunchRequest(url))
        }
    }

    @Test
    fun alarmRequiresBothAnExplicitRequestAndANonblankCampaignId() {
        assertEquals(LaunchRequest.None, parseLaunchRequest(campaignId = "alarm-1"))
        assertEquals(LaunchRequest.None, parseLaunchRequest(showAlarm = true, campaignId = " "))
        assertEquals(LaunchRequest.ShowAlarm("alarm-1"), parseLaunchRequest(showAlarm = true, campaignId = "alarm-1"))
    }

    @Test
    fun enforcementNotificationCanOpenWithoutAnInterceptedPackage() {
        val action = ACTION_SHOW_ENFORCEMENT
        assertEquals(LaunchRequest.Enforcement(null), parseLaunchRequest(action = action))
        assertEquals(
            LaunchRequest.Enforcement("example.entertainment"),
            parseLaunchRequest(action = action, interceptedPackage = "example.entertainment"),
        )
        assertEquals(LaunchRequest.None, parseLaunchRequest(interceptedPackage = "example.entertainment"))
    }
}
