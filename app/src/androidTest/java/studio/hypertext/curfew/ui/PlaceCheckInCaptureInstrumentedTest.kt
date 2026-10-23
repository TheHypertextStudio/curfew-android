package studio.hypertext.curfew.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.checkin.PlaceCheckIn
import studio.hypertext.curfew.ui.theme.CurfewTheme

/** Render-only fixtures: no enrollment is persisted and none of these codes can release an alarm. */
class PlaceCheckInCaptureInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun capturePlaces() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue("Run through the owned native matrix driver",
            InstrumentationRegistry.getArguments().getString("curfew.place.capture") == "true")
        org.junit.Assert.assertEquals("The driver must deny camera access before starting instrumentation",
            PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
        val draft = PlaceCheckIn.create("Bathroom")
        var scene by mutableStateOf("place-intro")
            compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
            compose.setContent { CurfewTheme { Surface {
                if (scene == "place-camera-denied") PlaceScanScreen("Bathroom", {}, {})
                else PlaceCheckInSetup(
                    draft = draft.takeIf { scene == "place-code" || scene == "place-error" },
                    enrollment = draft.enrollment.takeIf { scene == "place-ready" }, busy = false,
                    error = "Couldn’t save the code. Choose another location and try again.".takeIf { scene == "place-error" },
                    onCreate = {}, onExport = {}, onPrint = {}, onScan = {}, onCancel = {}, onReplace = {}, onRemove = {},
                    onAdvanced = {}, onDone = {}, onUseForAlarm = null, cameraReady = true,
                )
            } } }
            for (next in listOf("place-intro", "place-code", "place-ready", "place-error", "place-camera-denied")) {
                compose.runOnIdle { scene = next }
                val expected = when (next) {
                    "place-intro" -> "Create code"
                    "place-ready" -> "Ready for your alarm"
                    "place-camera-denied" -> "Allow camera"
                    else -> "Save code"
                }
                compose.waitUntil(5_000) { compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
                if (next == "place-code") compose.onNodeWithContentDescription("Your Bathroom check-in code").assertDoesNotExist()
                compose.waitForIdle()
                if (next == "place-code" && context.resources.configuration.screenWidthDp / context.resources.configuration.fontScale >= 640) {
                    compose.onNodeWithText("Save code").assertIsDisplayed()
                    compose.onNodeWithText("Print code").assertIsDisplayed()
                    compose.onNodeWithText("Scan code").assertIsDisplayed()
                    compose.onNodeWithText("Cancel code setup").assertIsDisplayed()
                }
                if (next == "place-error") {
                    compose.onNodeWithText("Couldn’t save the code. Choose another location and try again.").assertIsDisplayed()
                    compose.onNodeWithText("Save code").assertIsDisplayed()
                }
                val file = context.cacheDir.resolve("responsive-$next.png")
                Thread.sleep(150) // Allow the submitted frame to reach the system screenshot.
                file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
    }
}
