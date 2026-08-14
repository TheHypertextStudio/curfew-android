package studio.hypertext.curfew.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
                    maximumAttempts = 3,
                    conditionLabel = "Start the day",
                    actionLabel = "Open work surface",
                    onOpenAction = {},
                )
            }
        }

        compose.onNodeWithText("Attempt 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Checking Start the day").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").assertDoesNotExist()
        compose.onNodeWithText("Snooze").assertDoesNotExist()
        compose.onNodeWithText("Open work surface")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }
}
