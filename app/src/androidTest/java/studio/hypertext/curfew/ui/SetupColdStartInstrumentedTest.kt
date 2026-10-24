package studio.hypertext.curfew.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.persistence.FirstUsePreferences
import java.time.LocalDate
import java.time.LocalTime

class SetupColdStartInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val journals = SetupJournalFixture(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before fun isolateJournals(): Unit = runBlocking { journals.isolate() }
    @After fun restoreJournals(): Unit = runBlocking { journals.restore() }

    @Test fun exportRetainsItsIdentityAcrossRecreation(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = CurfewPreferences(context)
        val configurations = CallbackConfigurationRepository(context)
        val firstUse = FirstUsePreferences(context)
        firstUse.chooseNotNow(); firstUse.clearDraft()
        val connection = configurations.save("Export check-in", "https://export.example.test/wake", null)
        preferences.selectCallback(connection.callbackId)
        val uri = requireNotNull(context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "curfew-export-test.json")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
            }))
        // ActivityScenario cannot recreate a stopped owner behind DocumentsUI.
        // Intercept the picker launch, then deliver its result through Android's registry.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(android.content.IntentFilter(android.content.Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(android.content.Intent.CATEGORY_OPENABLE); addDataType("application/json") }, null, true)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("Settings").performClick()
                compose.onNodeWithText("Check-in").performClick()
                val initialAction = compose.onNodeWithText("Save setup file").performScrollTo().captureToImage().toPixelMap()
                val primaryColor = initialAction[initialAction.width / 2, 8]
                var requestCode = 0
                scenario.onActivity { activity ->
                    val saved = android.os.Bundle()
                    activity.activityResultRegistry.onSaveInstanceState(saved)
                    val keys = requireNotNull(saved.getStringArrayList("KEY_COMPONENT_ACTIVITY_REGISTERED_KEYS"))
                    requestCode = requireNotNull(saved.getIntegerArrayList("KEY_COMPONENT_ACTIVITY_REGISTERED_RCS"))[keys.indexOf("activity_rq#0")]
                }
                compose.onNodeWithText("Save setup file").performScrollTo().performClick()
                compose.waitUntil(5_000) { monitor.hits > 0 }
                scenario.recreate()
                scenario.onActivity { activity ->
                    activity.activityResultRegistry.dispatchResult(requestCode, android.app.Activity.RESULT_OK, android.content.Intent().setData(uri))
                }
                compose.waitUntil(5_000) {
                    runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().contains(connection.callbackId) } == true }.getOrDefault(false)
                }
                compose.onNodeWithText("Connected").assertDoesNotExist()
                val nextAction = compose.onNodeWithText("Test connection").performScrollTo().captureToImage().toPixelMap()
                org.junit.Assert.assertEquals(primaryColor, nextAction[nextAction.width / 2, 8])
                scenario.recreate()
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Test connection").fetchSemanticsNodes().isNotEmpty() }
                val restoredAction = compose.onNodeWithText("Test connection").performScrollTo().captureToImage().toPixelMap()
                org.junit.Assert.assertEquals(primaryColor, restoredAction[restoredAction.width / 2, 8])
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            context.contentResolver.delete(uri, null, null)
            configurations.delete(connection.callbackId)
        }
    }

    @Test fun keyboardKeepsBackAndTheServiceAddressVisible(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FirstUsePreferences(context).saveDraft(AlarmSetupDraft("keyboard", LocalTime.of(7, 0), LocalDate.now().plusDays(1), emptySet()))
        CurfewPreferences(context).selectCallback("missing-keyboard-fixture")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Advanced integration").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Advanced integration").performScrollTo().performClick()
            compose.onNodeWithText("I have a service").performScrollTo().performClick()
            compose.onNodeWithText("Service address").performScrollTo().performClick().performTextInput("https://wake.example.test/check-in")
            compose.waitUntil(5_000) {
                var keyboardVisible = false
                scenario.onActivity { activity ->
                    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                    keyboardVisible = insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true &&
                        insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom > 0
                }
                keyboardVisible
            }
            compose.onNodeWithContentDescription("Back").assertIsDisplayed()
            val back = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("The keyboard must not pan Back above the screen", back.top >= 0f)
            compose.onNodeWithText("Service address").assertIsDisplayed()
            val input = compose.onNode(hasSetTextAction() and hasText("Service address")).getUnclippedBoundsInRoot()
            val density = context.resources.displayMetrics.density
            var imeTop = 0f
            scenario.onActivity { activity ->
                val bottom = requireNotNull(androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView))
                    .getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                imeTop = (activity.window.decorView.height - bottom) / density
            }
            org.junit.Assert.assertTrue("The complete address field must stay below Back", input.top.value >= back.bottom / density)
            org.junit.Assert.assertTrue("The complete address field must stay above the keyboard", input.bottom.value <= imeTop)

        }
        FirstUsePreferences(context).chooseNotNow()
        FirstUsePreferences(context).clearDraft()
    }

    @Test fun alarmReadFailureShowsRetryInsteadOfAnotherSetup(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FirstUsePreferences(context).chooseNotNow()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("sunriseReadFailed\$delegate").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val state = field.get(activity) as androidx.compose.runtime.MutableState<Boolean>
                state.value = true
            }
            compose.onNodeWithText("Couldn’t load setup").assertExists()
            compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
            compose.onNodeWithText("Try again").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test fun verifiedDraftStartsAtPermissionsInsteadOfConnectionSetup(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = CurfewPreferences(context)
        val firstUse = FirstUsePreferences(context)
        val configurations = CallbackConfigurationRepository(context)
        val connection = configurations.save("Cold-start check-in", "https://coldstart.example.test/wake", null)
        preferences.selectCallback(connection.callbackId)
        preferences.markCallbackVerifiedIfSelected(connection.callbackId)
        preferences.setTestSoundConfirmed(false)
        firstUse.saveDraft(AlarmSetupDraft("cold-start", LocalTime.of(7, 0), LocalDate.now().plusDays(1), emptySet()))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Connection details").assertDoesNotExist()
                compose.onNodeWithText("I have a service").assertDoesNotExist()
                compose.onNodeWithText("Alarm setup").assertExists()
                scenario.recreate()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Alarm setup").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Alarm setup").assertExists()
                compose.onNodeWithText("Connection details").assertDoesNotExist()
                compose.onNodeWithText("I have a service").assertDoesNotExist()
            }
        } finally {
            firstUse.chooseNotNow(); firstUse.clearDraft(); configurations.delete(connection.callbackId)
        }
    }
}
