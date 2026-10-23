package studio.hypertext.curfew.ui

import androidx.test.espresso.Espresso
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.material3.MaterialTheme
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
    fun enlargedTextBringsTheFailureAndRetryIntoViewTogether() {
        val error = mutableStateOf<String?>(null)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                CurfewTheme {
                    Box(Modifier.width(320.dp).height(700.dp).testTag("deferral-viewport")) {
                        CheckInScreen(null, null, false, false, null, { _, _, _ -> "unused" }, {}, {}, {},
                            inSetup = true, onDefer = { error.value = "Couldn’t save your wake time. Try again." }, deferError = error.value)
                    }
                }
            }
        }
        compose.onNodeWithText("Save for later").performScrollTo().performClick()
        val errorBounds = compose.onNodeWithText("Couldn’t save your wake time. Try again.").getUnclippedBoundsInRoot()
        val retryBounds = compose.onNodeWithText("Save for later").getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("deferral-viewport").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("The complete error and retry must fit after failure: $errorBounds, $retryBounds, $viewport",
            errorBounds.top >= viewport.top && retryBounds.bottom <= viewport.bottom)
    }

    @Test
    fun fileHandoffHasPriorityBeforeTesting() {
        var primary = 0
        var exports = 0
        val saved = mutableStateOf(false)
        compose.setContent { CurfewTheme {
            primary = MaterialTheme.colorScheme.primary.toArgb()
            CheckInScreen("saved", "Morning check-in", false, false, null, { _, _, _ -> "saved" }, { exports++ }, {}, {}, setupFileSaved = saved.value)
        } }
        val pixels = compose.onNodeWithText("Save setup file").performScrollTo().captureToImage().toPixelMap()
        assertEquals("The first required action must use the primary container", primary, pixels[pixels.width / 2, 8].toArgb())
        compose.onNodeWithText("Save setup file").performClick()
        compose.runOnIdle { assertEquals(1, exports) }
        // A pressed button has a focus state layer; inspect the saved-state
        // transition rather than mistaking that layer for a new priority color.
        compose.runOnIdle { assertEquals(false, saved.value) }
        compose.runOnIdle { saved.value = true }
        val ready = compose.onNodeWithText("Test connection").performScrollTo().captureToImage().toPixelMap()
        assertEquals("Testing becomes the next action only after export succeeds", primary, ready[ready.width / 2, 8].toArgb())
        compose.onNodeWithText("Connected").assertDoesNotExist()
        compose.onNodeWithText("Continue").assertDoesNotExist()
    }

    @Test
    fun missingServiceExplainsThePrerequisiteAndOffersASafeExit() {
        var deferred = 0
        var saves = 0
        compose.setContent { CurfewTheme {
            CheckInScreen(null, null, false, false, null,
                onSave = { _, _, _ -> saves++; "unused" }, onExport = {}, onVerify = {}, onDone = {},
                inSetup = true, onDefer = { deferred++ })
        } }
        compose.onNodeWithText("Connect your check-in").assertExists()
        compose.onNodeWithText("Curfew doesn’t include a check-in service yet.").assertExists()
        compose.onNodeWithText("Service address").assertDoesNotExist()
        compose.onNodeWithText("Save for later").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, deferred); assertEquals(0, saves) }
        compose.onNodeWithText("Connect a service").performScrollTo().performClick()
        compose.onNodeWithText("You need a service that supports the Curfew check-in protocol. Curfew does not provide one yet. Use the HTTPS check-in endpoint from that service’s configuration.").assertExists()
        compose.onNodeWithText("Service address").assertExists()
    }

    @Test
    fun untestedConnectionExplainsWhoNeedsThePrivateFileBeforeTesting() {
        var exports = 0
        var tests = 0
        compose.setContent { CurfewTheme {
            CheckInScreen("saved", "Morning check-in", false, false, null,
                onSave = { _, _, _ -> "saved" }, onExport = { exports++ }, onVerify = { tests++ }, onDone = {})
        } }
        compose.onNodeWithText("1. Save the setup file.\n2. Import it into your service’s Curfew integration. If someone else manages the service, ask them to install it.\n3. Return here and test the connection.").assertExists()
        compose.onNodeWithText("Save setup file").performScrollTo().performClick()
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, exports); assertEquals(1, tests) }
        compose.onNodeWithText("Connected").assertDoesNotExist()
        compose.onNodeWithText("Continue").assertDoesNotExist()
    }

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
        compose.onNodeWithText("Name (optional)").performScrollTo().performTextInput("Morning check-in")
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
        compose.onNodeWithText("Name (optional)").performScrollTo().performTextInput("Morning check-in")
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
        compose.onNodeWithText("Name (optional)").performScrollTo().performTextInput("Morning check-in")
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
