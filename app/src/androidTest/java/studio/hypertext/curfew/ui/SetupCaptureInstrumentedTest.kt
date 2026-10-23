package studio.hypertext.curfew.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import org.junit.Before
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.readiness.*
import studio.hypertext.curfew.ui.theme.CurfewTheme

class SetupCaptureInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = object : MotionDurationScale { override val scaleFactor = 1f })
    private var saves = 0
    @Before fun prepareWindow() {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    @Test fun setupUsesTheAvailableWindowWithoutActivatingAnAlarm() {
        val callback = mutableStateOf<String?>(null)
        val verified = mutableStateOf(false)
        val exported = mutableStateOf(false)
        val required = mutableStateOf(setOf(ReadinessAction.GRANT_FULL_SCREEN_ACCESS))
        compose.setContent { CurfewTheme {
            CurfewApp(
                readiness = AlarmReadiness(required.value.isEmpty(), required.value, emptySet(), AlarmPresentation.FULL_SCREEN),
                accountEnrolled = false, accountSignedIn = false, accountRecoveryState = null,
                onGrantExactAlarm = {}, onGrantNotifications = {}, onRepairAlarmChannel = {}, onPlayTestSound = {},
                onAcknowledgeLimitations = {}, onOpenAccount = {}, onEnrollAccount = {}, onRestoreRecoveryKey = {},
                onAcknowledgeRecoveryKey = {},
                onSaveCallback = { _, _, _ -> callback.value = "capture"; "capture" },
                sunriseAlarm = null, callbackId = callback.value, conditionLabel = callback.value?.let { "Morning check-in" },
                alarmBusy = false, alarmError = null, onSaveAlarm = { saves++ }, onToggleAlarm = {},
                startSetup = true, conditionVerified = verified.value,
                onVerifyCondition = { verified.value = true }, onGrantFullScreen = { required.value = emptySet() },
                onWelcomeChoice = { throw java.io.IOException("capture storage failure") },
                onExportCondition = { exported.value = true }, conditionSetupFileSaved = exported.value,
            )
        } }
        capture("time")
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        capture("introduction")
        compose.onNodeWithText("Save for later").performScrollTo().performClick()
        capture("defer-error")
        val errorBounds = compose.onNodeWithText("Couldn’t save your wake time. Try again.").getUnclippedBoundsInRoot()
        val retryBounds = compose.onNodeWithText("Save for later").getUnclippedBoundsInRoot()
        val viewport = compose.onRoot().getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("The full error and retry must be visible after failure", errorBounds.top >= viewport.top && retryBounds.bottom <= viewport.bottom)
        compose.onNodeWithText("Connect a service").performScrollTo().performClick()
        capture("connection")
        // Short windows and visible connection details can require scrolling.
        // The entire submit action must be reachable within the viewport.
        compose.onNodeWithText("Save check-in").performScrollTo()
        val action = compose.onNodeWithText("Save check-in").getUnclippedBoundsInRoot()
        val connectionViewport = compose.onRoot().getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("The full Save action must be reachable", action.top >= connectionViewport.top && action.bottom <= connectionViewport.bottom)
        compose.onNodeWithText("Service address").performScrollTo().performClick().performTextInput("https://wake.example.test/check-in")
        compose.waitUntil(5_000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        // IME visibility precedes the final scroll/resize frame on some profiles.
        // Wait for the same whole-field invariant asserted below, rather than a fixed delay.
        compose.waitUntil(5_000) {
            val bounds = compose.onNode(hasSetTextAction() and hasText("Service address")).getUnclippedBoundsInRoot()
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            val bottom = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0
            bottom > 0 && bounds.bottom.value <= (compose.activity.window.decorView.height - bottom) / compose.activity.resources.displayMetrics.density
        }
        val input = compose.onNode(hasSetTextAction() and hasText("Service address")).getUnclippedBoundsInRoot()
        val back = compose.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val ime = requireNotNull(androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView))
            .getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
        val keyboardTop = (compose.activity.window.decorView.height - ime) / density
        org.junit.Assert.assertTrue("The input must not overlap Back: $input, $back", input.top >= back.bottom || input.left >= back.right || input.right <= back.left)
        org.junit.Assert.assertTrue("The whole input must stay above the IME: $input, $keyboardTop", input.bottom.value <= keyboardTop)
        capture("keyboard")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save check-in").performScrollTo().performClick()
        capture("transfer")
        if (compose.activity.resources.configuration.fontScale >= 1.5f) {
            compose.onNodeWithText("Test connection").performScrollTo()
            capture("transfer-actions")
        }
        compose.onNodeWithText("Save setup file").performScrollTo().performClick()
        capture("test-ready")
        if (compose.activity.resources.configuration.fontScale >= 1.5f) {
            compose.onNodeWithText("Test connection").performScrollTo()
            capture("test-ready-actions")
        }
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        capture("connected")
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        capture("permissions")
        compose.onNodeWithText("Allow alarm screen").performScrollTo().performClick()
        capture("review")
        if (compose.activity.resources.configuration.fontScale >= 1.5f) {
            compose.onNodeWithText("Turn on alarm").performScrollTo()
            capture("review-actions")
        }
        // Capture the actual shared wake-time transition with the navigation clock controlled.
        compose.onNodeWithContentDescription("Edit wake time").performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Edit wake time").performClick()
        for (time in listOf(16L, 96L, 240L, 800L)) {
            compose.mainClock.advanceTimeBy(if (time == 16L) 16 else if (time == 96L) 80 else if (time == 240L) 144 else 560)
            capture("motion-$time")
        }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(0, saves) }
    }

    private fun capture(scene: String) {
        val expected = when (scene) {
            "time" -> "Once"
            "introduction" -> "Connect a service"
            "defer-error" -> "Couldn’t save your wake time. Try again."
            "connection", "keyboard" -> "Service address"
            "transfer", "transfer-actions" -> "Test connection"
            "test-ready", "test-ready-actions" -> "Test connection"
            "connected" -> "Connected"
            "permissions" -> "Allow alarm screen"
            "review", "review-actions" -> "Turn on alarm"
            else -> null
        }
        if (expected != null) {
            compose.waitUntil(5_000) { compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(expected).assertExists()
        }
        compose.waitForIdle()
        // The system screenshot includes IME and bars; allow the submitted Surface frame to reach the compositor.
        Thread.sleep(150)
        val output = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("setup-$scene.png")
        output.outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
