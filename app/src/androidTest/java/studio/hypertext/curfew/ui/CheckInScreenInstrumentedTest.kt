package studio.hypertext.curfew.ui

import androidx.test.espresso.Espresso
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
    fun saveWaitsForPersistenceBeforeOfferingConnectionActions() {
        val busy = mutableStateOf(false)
        val callback = mutableStateOf<String?>(null)
        var saves = 0
        compose.setContent {
            CurfewTheme {
                CheckInScreen(callback.value, callback.value?.let { "Morning check-in" }, false, busy.value, null,
                    onSave = { _, _, _ -> saves++; busy.value = true; "condition-1" }, onExport = {}, onVerify = {}, onDone = {})
            }
        }
        compose.onNodeWithText("Test connection").assertDoesNotExist()
        compose.onNodeWithText("Connect a service").performScrollTo().performClick()
        compose.onNodeWithText("Optional details").performScrollTo().performClick()
        compose.onNodeWithText("Name").performScrollTo().performTextInput("Morning check-in")
        compose.onNodeWithText("Service address").performTextInput("https://wake.example.test/check-in")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save check-in").performScrollTo().performClick()
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, saves); callback.value = "condition-1" }
        compose.onNodeWithText("Save setup file").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { busy.value = false }
        compose.onNodeWithText("Test connection").performScrollTo().assertIsEnabled()
    }

    @Test
    fun checkInPreservesOpaqueAppLinks() {
        var savedAction: String? = null
        compose.setContent {
            CurfewTheme {
                CheckInScreen(null, null, false, false, null,
                    onSave = { _, _, action -> savedAction = action; "condition-1" }, onExport = {}, onVerify = {}, onDone = {})
            }
        }
        compose.onNodeWithText("Connect a service").performScrollTo().performClick()
        compose.onNodeWithText("Optional details").performScrollTo().performClick()
        compose.onNodeWithText("Name").performScrollTo().performTextInput("Morning check-in")
        compose.onNodeWithText("Service address").performTextInput("https://wake.example.test/check-in")
        compose.onNodeWithText("Check-in link (optional)").performScrollTo().performTextInput("morning-checkin://start")
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
                    onSave = { _, _, _ -> saves++; message.value = "Could not save check-in. Try again."; throw java.io.IOException("storage") },
                    onExport = {}, onVerify = {}, onDone = {})
            }
        }
        compose.onNodeWithText("Connect a service").performScrollTo().performClick()
        compose.onNodeWithText("Optional details").performScrollTo().performClick()
        compose.onNodeWithText("Name").performScrollTo().performTextInput("Morning check-in")
        compose.onNodeWithText("Service address").performTextInput("https://wake.example.test/check-in")
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Save check-in").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
        compose.onNodeWithText("Couldn’t save the check-in. Try again.").assertExists()
        compose.onNodeWithText("Save check-in").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, saves) }
    }

    @Test
    fun savedConnectionCanArriveAfterBusyClears() {
        val callback = mutableStateOf<String?>(null)
        val busy = mutableStateOf(false)
        compose.setContent { CurfewTheme {
            CheckInScreen(callback.value, callback.value?.let { "Saved check-in" }, false, busy.value, null,
                { _, _, _ -> busy.value = true; "saved" }, {}, {}, {})
        } }
        compose.onNodeWithText("Connect a service").performScrollTo().performClick()
        compose.onNodeWithText("Service address").performTextInput("https://wake.example.test/check-in")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save check-in").performScrollTo().performClick()
        compose.runOnIdle { busy.value = false }
        compose.runOnIdle { callback.value = "saved" }
        compose.onNodeWithText("Test connection").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun relativeOpeningLinksCannotBeSaved() {
        compose.setContent {
            CurfewTheme { CheckInScreen(null, null, false, false, null, { _, _, _ -> "condition-1" }, {}, {}, {}) }
        }
        compose.onNodeWithText("Connect a service").performScrollTo().performClick()
        compose.onNodeWithText("Service address").performTextInput("https://wake.example.test/check-in")
        compose.onNodeWithText("Optional details").performScrollTo().performClick()
        compose.onNodeWithText("Check-in link (optional)").performTextInput("/relative/path")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save check-in").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun anUnchangedConnectedDetailKeepsFailuresWithoutADuplicateExit() {
        compose.setContent {
            CurfewTheme {
                CheckInScreen("condition-1", "Morning check-in", true, false,
                    "Couldn’t save the setup file. Try again.",
                    onSave = { _, _, _ -> "condition-1" }, onExport = {}, onVerify = {}, onDone = {})
            }
        }
        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("Couldn’t save the setup file. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @Test
    fun aNewConnectionKeepsACompletionActionAfterVerification() {
        val verified = mutableStateOf(false)
        var exports = 0
        var completions = 0
        compose.setContent {
            CurfewTheme {
                CheckInScreen("condition-1", "Morning check-in", verified.value, false, null,
                    onSave = { _, _, _ -> "condition-1" }, onExport = { exports++ },
                    onVerify = { verified.value = true }, onDone = { completions++ })
            }
        }
        compose.onNodeWithText("Save setup file").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, exports) }
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("Connection details").performClick()
        compose.onNodeWithText("Replace check-in").performScrollTo().performClick()
        compose.onNodeWithText("Keep current check-in").performScrollTo().performClick()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, completions) }
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

}
