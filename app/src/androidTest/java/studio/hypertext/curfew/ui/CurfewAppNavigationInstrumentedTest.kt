package studio.hypertext.curfew.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseOccurrence
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.readiness.AlarmPresentation
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.ReadinessAction
import studio.hypertext.curfew.ui.theme.CurfewTheme

class CurfewAppNavigationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val saved = SavedSunriseAlarm(
        SunriseSchedule("sunrise-1", LocalTime.of(7, 0), LocalDate.of(2026, 10, 20), emptySet(), true, "condition-1"),
        SunriseOccurrence("morning-1", LocalDate.of(2026, 10, 20), Instant.parse("2026-10-20T14:00:00Z")),
    )
    private val failure = mutableStateOf<String?>(null)
    private var saves = 0
    private var volumeRequests = 0
    private val missingActions = mutableStateOf<Set<ReadinessAction>>(emptySet())

    private fun show(required: Set<ReadinessAction> = emptySet(), failSave: Boolean = false) {
        missingActions.value = required
        compose.setContent {
            CurfewTheme {
                CurfewApp(
                    readiness = AlarmReadiness(missingActions.value.isEmpty(), missingActions.value, emptySet(), AlarmPresentation.FULL_SCREEN),
                    accountEnrolled = false,
                    accountSignedIn = false,
                    accountRecoveryState = null,
                    onGrantExactAlarm = {},
                    onGrantNotifications = {},
                    onRepairAlarmChannel = {},
                    onPlayTestSound = {},
                    onAcknowledgeLimitations = {},
                    onOpenAccount = {},
                    onEnrollAccount = {},
                    onRestoreRecoveryKey = {},
                    onAcknowledgeRecoveryKey = {},
                    onSaveCallback = { _, _, _ -> },
                    sunriseAlarm = saved,
                    callbackId = "condition-1",
                    conditionLabel = "Morning check-in",
                    alarmBusy = false,
                    alarmError = failure.value,
                    onSaveAlarm = { saves++; if (failSave) failure.value = "Could not save the alarm. Try again." },
                    onToggleAlarm = {},
                    onRaiseAlarmVolume = { volumeRequests++; missingActions.value = emptySet() },
                    conditionVerified = true,
                )
            }
        }
    }

    @Test
    fun dailyShowsSavedAlarmWithoutSetupForms() {
        show()
        compose.onNodeWithContentDescription("Edit Sunrise").assertIsDisplayed()
        compose.onNodeWithText("Morning check-in").assertIsDisplayed()
        compose.onNodeWithText("HTTPS verifier endpoint").assertDoesNotExist()
        compose.onNodeWithText("Curfew Recovery Key").assertDoesNotExist()
        compose.onNodeWithText("Save Sunrise").assertDoesNotExist()
        compose.onNodeWithText("Sign in and enroll device").assertDoesNotExist()
    }

    @Test
    fun settingsSeparatesAccountAndCheckInTasks() {
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Account").performClick()
        compose.onNodeWithText("Sign in").assertIsDisplayed()
        compose.onNodeWithText("HTTPS verifier endpoint").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Check-in").performClick()
        compose.onNodeWithText("Morning check-in").assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun editBackDiscardsAllDraftChanges() {
        show()
        compose.onNodeWithContentDescription("Edit Sunrise").performClick()
        compose.onNodeWithText("Weekly").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Edit Sunrise").performClick()
        compose.onNodeWithText("One night").assertIsSelected()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun failedSaveKeepsDraftAndEditorOpen() {
        show(failSave = true)
        compose.onNodeWithContentDescription("Edit Sunrise").performClick()
        compose.onNodeWithText("Weekly").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Could not save the alarm. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Weekly").assertIsSelected()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun readinessUsesTheActualMissingAction() {
        show(required = setOf(ReadinessAction.RAISE_ALARM_VOLUME))
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Alarm access and sound").performClick()
        compose.onNodeWithText("Turn up alarm volume").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, volumeRequests) }
        compose.onNodeWithText("HTTPS verifier endpoint").assertDoesNotExist()
    }
    @Test
    fun setupReturnsToTheDraftAndBackStillLeavesTheEditor() {
        show(required = setOf(ReadinessAction.RAISE_ALARM_VOLUME))
        compose.onNodeWithContentDescription("Edit Sunrise").performClick()
        compose.onNodeWithText("Weekly").performClick()
        compose.onNodeWithText("Finish setup").performClick()
        compose.onNodeWithText("Turn up alarm volume").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Done").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Weekly").assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Edit Sunrise").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun anUnchangedSaveCanFinishWithoutAnIntermediateBusyFrame() {
        show()
        compose.onNodeWithContentDescription("Edit Sunrise").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithContentDescription("Edit Sunrise").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun destinationsExposeTheirNamesToAssistiveTechnology() {
        show()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Sunrise")).assertExists()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Settings")).assertExists()
        compose.onNodeWithText("Settings").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

}
