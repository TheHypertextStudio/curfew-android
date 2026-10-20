package studio.hypertext.curfew.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class CheckInScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun checkInPreservesOpaqueAppLinks() {
        var savedAction: String? = null
        compose.setContent {
            CurfewTheme {
                CheckInScreen(null, null, false, false, null,
                    onSave = { _, _, action -> savedAction = action }, onExport = {}, onVerify = {}, onDone = {})
            }
        }
        compose.onNodeWithText("Name").performTextInput("Morning check-in")
        compose.onNodeWithText("Service address").performTextInput("https://wake.example.test/check-in")
        compose.onNodeWithText("Check-in link (optional)").performTextInput("morning-checkin://start")
        compose.onNodeWithText("Save check-in").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("morning-checkin://start", savedAction) }
    }
    @Test
    fun aFailedSaveAllowsRetryWithoutAnIntermediateBusyFrame() {
        val message = mutableStateOf<String?>(null)
        var saves = 0
        compose.setContent {
            CurfewTheme {
                CheckInScreen(null, null, false, false, message.value,
                    onSave = { _, _, _ -> saves++; message.value = "Could not save check-in. Try again." },
                    onExport = {}, onVerify = {}, onDone = {})
            }
        }
        compose.onNodeWithText("Name").performTextInput("Morning check-in")
        compose.onNodeWithText("Service address").performTextInput("https://wake.example.test/check-in")
        compose.onNodeWithText("Save check-in").performScrollTo().performClick()
        compose.onNodeWithText("Save check-in").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, saves) }
    }

}
