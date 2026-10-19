package studio.hypertext.curfew.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class WakeConditionCardInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun savingWaitsForPersistenceAndTestingWaitsForAStoredCondition() {
        val saving = mutableStateOf(false)
        val savedLabel = mutableStateOf<String?>(null)
        var saved = 0
        compose.setContent {
            CurfewTheme(dynamicColor = false) {
                WakeConditionCard(savedLabel.value, false, saving.value, null,
                    { _, _, _ -> saved++; saving.value = true }, {}, {})
            }
        }
        compose.onNodeWithText("Test connection").assertIsNotEnabled()
        compose.onNodeWithText("Condition name").performTextInput("Start the day")
        compose.onNodeWithText("HTTPS verifier endpoint").performTextInput("https://verifier.example.test/wake")
        compose.onNodeWithText("Save condition").performClick()
        compose.onNodeWithText("Working…").assertIsNotEnabled()
        compose.onNodeWithText("Export verifier setup").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, saved); savedLabel.value = "Start the day"; saving.value = false }
        compose.onNodeWithText("Test connection").assertIsEnabled()
        compose.onNodeWithText("Export verifier setup").assertIsEnabled()
    }
}
