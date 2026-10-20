package studio.hypertext.curfew.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class AlarmRingingScreenInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun recoveryOpensASeparateConfirmation() {
        var requests = 0
        compose.setContent {
            CurfewTheme {
                AlarmRingingScreen("07:00", "Morning check-in", null, {}, { requests++ })
            }
        }
        compose.onNodeWithText("Alarm recovery").performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun ringingScreenHasNoDismissOrSnoozeAndKeepsTheActionAccessible() {
        compose.setContent {
            CurfewTheme(dynamicColor = false) {
                AlarmRingingScreen(
                    time = "07:00",
                    conditionLabel = "Start the day",
                    actionLabel = "Open work surface",
                    onOpenAction = {},
                    onRecover = {},
                )
            }
        }

        compose.onNodeWithText("Time to get up.").assertIsDisplayed()
        compose.onNodeWithText("07:00").assertIsDisplayed()
        compose.onNodeWithText("Waiting for Start the day").assertIsDisplayed()
        compose.onAllNodesWithText("Dismiss").assertCountEquals(0)
        compose.onAllNodesWithText("Snooze").assertCountEquals(0)
        compose.onNodeWithText("Open work surface")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }
}
