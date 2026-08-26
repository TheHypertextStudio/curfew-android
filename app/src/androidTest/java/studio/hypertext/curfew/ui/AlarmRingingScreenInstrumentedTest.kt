package studio.hypertext.curfew.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class AlarmRingingScreenInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun ringingScreenHasNoDismissOrSnoozeAndKeepsTheActionAccessible() {
        compose.setContent {
            CurfewTheme(dynamicColor = false) {
                AlarmRingingScreen(
                    attempt = 1,
                    conditionLabel = "Start the day",
                    actionLabel = "Open work surface",
                    onOpenAction = {},
                )
            }
        }

        compose.onNodeWithText("Wake attempt 1").assertIsDisplayed()
        compose.onNodeWithText("Checking Start the day").assertIsDisplayed()
        compose.onAllNodesWithText("Dismiss").assertCountEquals(0)
        compose.onAllNodesWithText("Snooze").assertCountEquals(0)
        compose.onNodeWithText("Open work surface")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }
}
