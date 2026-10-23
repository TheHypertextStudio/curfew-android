package studio.hypertext.curfew.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class PlaceCameraLifecycleInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun cancellingScannerReleasesItsActualCameraClient() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.grantRuntimePermission(pkg, Manifest.permission.CAMERA)
        var scanning by mutableStateOf(true)
        compose.setContent { CurfewTheme {
            if (scanning) PlaceScanScreen("Fixture bathroom", {}, { scanning = false })
            else Text("Scanner closed")
        } }
        fun activeClient(): Boolean {
            val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys media.camera")
            val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            val clients = dump.substringAfter("Active Camera Clients:", "").substringBefore("Allowed user IDs:")
            return pkg in clients
        }
        try {
            compose.waitUntil(15_000) { activeClient() }
            compose.onNodeWithContentDescription("Camera preview for Fixture bathroom code").assertExists()
            compose.onNodeWithText("Cancel scan").performScrollTo().performClick()
            compose.onNodeWithText("Scanner closed").assertExists()
            compose.waitUntil(10_000) { !activeClient() }
        } finally {
            compose.runOnIdle { scanning = false }
        }
    }
}
