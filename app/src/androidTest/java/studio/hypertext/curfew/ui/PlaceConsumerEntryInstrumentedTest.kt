package studio.hypertext.curfew.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.persistence.FirstUsePreferences

class PlaceConsumerEntryInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val journals = SetupJournalFixture(InstrumentationRegistry.getInstrumentation().targetContext)
    @Before fun isolate(): Unit = runBlocking { journals.isolate() }
    @After fun restore(): Unit = runBlocking { journals.restore() }

    @Test fun ordinaryCheckInNeedsNeitherEndpointNorPrivateKey(): Unit = runBlocking {
        FirstUsePreferences(InstrumentationRegistry.getInstrumentation().targetContext).chooseNotNow()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Check-in").performClick()
            compose.onNodeWithText("Scan a code away from bed").assertExists()
            compose.onNodeWithText("Service address").assertDoesNotExist()
            compose.onNodeWithText("Save setup file").assertDoesNotExist()
            compose.onNodeWithText("Advanced integration").performScrollTo().assertExists()
        }
    }
}
