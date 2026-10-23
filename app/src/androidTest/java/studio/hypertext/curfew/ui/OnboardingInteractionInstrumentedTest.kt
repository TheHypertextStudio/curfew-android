package studio.hypertext.curfew.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.readiness.AlarmSoundCheckState
import studio.hypertext.curfew.ui.theme.CurfewTheme
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule

class OnboardingInteractionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun soundPreviewCanBePlayedStoppedAndConfirmedInPlace() {
        val state = mutableStateOf(AlarmSoundCheckState.CLOSED)
        var confirmed = 0
        compose.setContent { CurfewTheme {
            AlarmSoundTestContent(state.value,
                onPlay = { state.value = AlarmSoundCheckState.PLAYING },
                onConfirm = { confirmed++ },
                onStop = { state.value = AlarmSoundCheckState.CLOSED })
        } }
        compose.onNodeWithText("Play alarm preview").performClick()
        compose.onNodeWithText("Listen for your alarm").assertIsDisplayed()
        compose.onNodeWithText("Stop preview").performClick()
        compose.onNodeWithText("Play alarm preview").assertIsDisplayed()
        compose.runOnIdle { state.value = AlarmSoundCheckState.AWAITING_CONFIRMATION }
        compose.onNodeWithText("Play again").performClick()
        compose.runOnIdle { state.value = AlarmSoundCheckState.AWAITING_CONFIRMATION }
        compose.onNodeWithText("I heard it").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun connectionDetailsAreVisibleWithoutOpeningAnExpander() {
        compose.setContent { CurfewTheme {
            CheckInScreen(null, null, false, false, null, { _, _, _ -> "saved" }, {}, {}, {},
                state = CheckInState().apply { editing = true })
        } }
        compose.onNodeWithText("Name (optional)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Check-in link (optional)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Optional details").assertDoesNotExist()
    }

    @Test fun editingDuringSaveKeepsNewerValuesAndIdentifiesTheSavedSnapshot() {
        val alarm = mutableStateOf<SavedSunriseAlarm?>(null)
        val busy = mutableStateOf(false)
        var pending: SunriseSchedule? = null
        compose.setContent { CurfewTheme {
            SunriseScheduleEditor(alarm.value, "check-in", true, busy.value, null,
                onSave = { pending = it; busy.value = true }, onFinishSetup = {})
        } }
        compose.onNodeWithText("Save alarm").performScrollTo().performClick()
        compose.onNodeWithText("Repeat").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            alarm.value = SavedSunriseAlarm(requireNotNull(pending), null, null)
            busy.value = false
        }
        compose.onNodeWithText("Previous changes saved. You have unsaved changes.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save alarm").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(null, requireNotNull(pending).onceDate)
            assertEquals(7, requireNotNull(pending).weekdays.size)
            alarm.value = SavedSunriseAlarm(requireNotNull(pending), null, null)
            busy.value = false
        }
        compose.onNodeWithText("Alarm saved.").performScrollTo().assertIsDisplayed()
    }
}
