package studio.hypertext.curfew.ui

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.checkin.PlaceCheckIn
import studio.hypertext.curfew.ui.theme.CurfewTheme

/** Injected window geometry, not a claim about a physical notch or navigation mode. */
class ScreenInsetsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun standaloneScreensKeepActionsOutsideCutoutsAndSystemBars() {
        val scene = mutableStateOf("ringing")
        val draft = PlaceCheckIn.create("Bathroom")
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent { CurfewTheme { Surface {
            when (scene.value) {
                "ringing" -> AlarmRingingScreen("07:00", "Bathroom", "Scan code", {}, {})
                "recovery" -> AlarmRecoveryScreen("Bathroom", false, false, null, {}, {}, {}, {}, {})
                "scanner" -> PlaceScanScreen("Bathroom", {}, {})
                "welcome" -> CurfewWelcomeScreen({}, {}, motionEnabled = false)
                "load-error" -> FirstUseLoadErrorScreen({})
                else -> PlaceCheckInSetup(draft, null, false, null, {}, {}, {}, {}, {}, {}, {}, {}, {}, null, true)
            }
        } } }
        for ((screen, actions) in listOf(
            "ringing" to listOf("Scan code", "Alarm recovery"),
            "recovery" to listOf("Open security settings"),
            "scanner" to listOf("Cancel scan"),
            "welcome" to listOf("Get started", "Not now"),
            "load-error" to listOf("Try again"),
            "place" to listOf("Save code", "Print code", "Scan code", "Cancel code setup"),
        )) {
            compose.runOnIdle { scene.value = screen }
            compose.waitForIdle()
            injectInsets()
            for (action in actions) {
                val node = compose.onNodeWithText(action)
                if (screen != "ringing" && screen != "recovery") node.performScrollTo()
                node.assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().boundsInWindow
                val root = compose.onRoot().fetchSemanticsNode().boundsInWindow
                assertTrue("$screen/$action overlaps left cutout: $bounds", bounds.left >= 219f)
                assertTrue("$screen/$action overlaps status bar: $bounds", bounds.top >= 79f)
                assertTrue("$screen/$action overlaps side navigation: $bounds", bounds.right <= root.right - 139f)
                assertTrue("$screen/$action overlaps bottom navigation: $bounds", bounds.bottom <= root.bottom - 99f)
            }
        }
    }

    @Test fun appNavigationConsumesSafeInsetsOnceAcrossDestinations() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent { CurfewTheme {
            CurfewApp(
                readiness = studio.hypertext.curfew.readiness.AlarmReadiness(false,
                    setOf(studio.hypertext.curfew.readiness.ReadinessAction.GRANT_FULL_SCREEN_ACCESS),
                    emptySet(), studio.hypertext.curfew.readiness.AlarmPresentation.FULL_SCREEN),
                accountEnrolled = false, accountSignedIn = false, accountRecoveryState = null,
                onGrantExactAlarm = {}, onGrantNotifications = {}, onRepairAlarmChannel = {}, onPlayTestSound = {},
                onAcknowledgeLimitations = {}, onOpenAccount = {}, onEnrollAccount = {},
                onRestoreRecoveryKey = {}, onAcknowledgeRecoveryKey = {}, onSaveCallback = { _, _, _ -> "unused" },
                sunriseAlarm = null, callbackId = null, conditionLabel = null,
                alarmBusy = false, alarmError = null, onSaveAlarm = {}, onToggleAlarm = {},
            )
        } }
        injectInsets()
        safe(compose.onNodeWithContentDescription("Settings"), "Settings")
        safe(compose.onNodeWithContentDescription("Edit alarm"), "Edit alarm")
        compose.onNodeWithContentDescription("Edit alarm").performClick()
        injectInsets()
        safe(compose.onNodeWithContentDescription("Back"), "Wake time back")
        val backLeft = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInWindow.left
        assertTrue("Nested bars must not pad the cutout twice", backLeft < 350f)
        safe(compose.onNodeWithText("Continue").performScrollTo(), "Continue")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        injectInsets()
        safe(compose.onNodeWithText("Account").performScrollTo(), "Account")
        compose.onNodeWithText("Account").performClick()
        injectInsets()
        safe(compose.onNodeWithText("Sign in").performScrollTo(), "Sign in")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Check-in").performScrollTo().performClick()
        injectInsets()
        safe(compose.onNodeWithText("I have a service").performScrollTo(), "Check-in")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Alarm setup").performScrollTo().performClick()
        injectInsets()
        safe(compose.onNodeWithText("Allow alarm screen").performScrollTo(), "Alarm setup")
    }

    @Test fun alarmBackgroundStillDrawsBehindSystemInsets() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent { CurfewTheme(darkTheme = false) {
            AlarmRingingScreen("07:00", "Bathroom", "Scan code", {}, {})
        } }
        injectInsets()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val surface = studio.hypertext.curfew.ui.theme.CurfewLightColorScheme.surface.toArgb()
        org.junit.Assert.assertEquals("Safe controls must not inset the background", surface, image.getPixel(2, image.height / 2))
        org.junit.Assert.assertEquals("Background must extend under side navigation", surface, image.getPixel(image.width - 3, image.height / 2))
    }

    private fun injectInsets() {
        compose.waitForIdle()
        compose.runOnUiThread {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 80, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 140, 100))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(220, 0, 0, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
        }
        compose.waitForIdle()
    }

    private fun safe(node: SemanticsNodeInteraction, label: String) {
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val root = compose.onRoot().fetchSemanticsNode().boundsInWindow
        assertTrue("$label overlaps cutout/status: $bounds", bounds.left >= 219f && bounds.top >= 79f)
        assertTrue("$label overlaps navigation: $bounds", bounds.right <= root.right - 139f && bounds.bottom <= root.bottom - 99f)
    }

}
