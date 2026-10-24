package studio.hypertext.curfew.ui

import studio.hypertext.curfew.alarm.AlarmSetupDraft

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertTrue
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
    private val hasConnection = mutableStateOf(true)
    private var saves = 0
    private val callbackGeneration = mutableStateOf(0)
    private var verifiedGeneration = -1
    private var exportedGeneration = -1
    private var volumeRequests = 0
    private var fullScreenRequests = 0
    private val missingActions = mutableStateOf<Set<ReadinessAction>>(emptySet())

    private fun show(required: Set<ReadinessAction> = emptySet(), failSave: Boolean = false, fresh: Boolean = false, welcome: Boolean = fresh, deferredDraft: AlarmSetupDraft? = null, replacement: Boolean = false) {
        missingActions.value = required
        compose.setContent {
            CurfewTheme {
                val generation = callbackGeneration.value
                CurfewApp(
                    readiness = AlarmReadiness(missingActions.value.isEmpty(), missingActions.value, emptySet(), AlarmPresentation.FULL_SCREEN),
                    accountEnrolled = false,
                    accountSignedIn = false,
                    accountRecoveryState = null,
                    onGrantExactAlarm = {},
                    onGrantNotifications = {},
                    onGrantFullScreen = { fullScreenRequests++; missingActions.value = emptySet() },
                    onRepairAlarmChannel = {},
                    onPlayTestSound = {},
                    onAcknowledgeLimitations = {},
                    onOpenAccount = {},
                    onEnrollAccount = {},
                    onRestoreRecoveryKey = {},
                    onAcknowledgeRecoveryKey = {},
                    onSaveCallback = { _, _, _ -> "condition-1" },
                    sunriseAlarm = if (fresh) null else saved,
                    showWelcome = welcome,
                    setupDraft = deferredDraft,
                    callbackId = if (replacement) "replacement" else if (hasConnection.value) "condition-1" else null,
                    conditionLabel = if (replacement) "Replacement check-in" else if (hasConnection.value) "Morning check-in" else null,
                    alarmBusy = false,
                    alarmError = failure.value,
                    onSaveAlarm = { saves++; if (failSave) failure.value = "Could not save the alarm. Try again." },
                    onToggleAlarm = {},
                    onRaiseAlarmVolume = { volumeRequests++; missingActions.value = emptySet() },
                    conditionVerified = hasConnection.value,
                    onVerifyCondition = { verifiedGeneration = generation },
                    onExportCondition = { exportedGeneration = generation },
                )
            }
        }
    }

    @Test
    fun scaledWindowKeepsDailyAndEditorActionsReachable() {
        show()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit alarm").assertIsDisplayed()
        captureResponsiveWindow("daily")
        compose.onNodeWithContentDescription("Edit alarm").performClick()
        compose.onNodeWithText("Repeat").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Repeat on Sunday").performScrollTo().assertIsDisplayed()
        captureResponsiveWindow("editor")
        compose.onNodeWithText("Save alarm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        captureResponsiveWindow("settings")
        compose.onAllNodesWithText("Account").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Sign in").performScrollTo().assertIsDisplayed()
        captureResponsiveWindow("account")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { hasConnection.value = false }
        compose.onNodeWithText("Check-in").performScrollTo().performClick()
        compose.onNodeWithText("I have a service").performScrollTo().assertIsDisplayed()
        captureResponsiveWindow("check-in")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { missingActions.value = setOf(ReadinessAction.GRANT_FULL_SCREEN_ACCESS) }
        compose.onNodeWithText("Alarm setup").performScrollTo().performClick()
        compose.onNodeWithText("Allow alarm screen").performScrollTo().assertIsDisplayed()
        captureResponsiveWindow("readiness")
        compose.runOnIdle { assertEquals(0, saves) }
    }

    private fun captureResponsiveWindow(scene: String) {
        val output = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("responsive-$scene.png")
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun setupDisclosesTheFirstUnlockRequirement() {
        show(setOf(ReadinessAction.ACKNOWLEDGE_LIMITATIONS))
        compose.onNodeWithText("Finish setup").performScrollTo().performClick()
        compose.onNodeWithText("Unlock your phone after restarting so alarms can resume.", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("I understand").performScrollTo().assertIsDisplayed()
        val output = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("first-unlock-readiness.png")
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun dailyShowsSavedAlarmWithoutSetupForms() {
        show()
        compose.onNodeWithContentDescription("Edit alarm").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("Edit alarm").performClick()
        compose.onNodeWithText("Repeat").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Edit alarm").performClick()
        compose.onNodeWithText("Once").assertIsSelected()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun failedSaveKeepsDraftAndEditorOpen() {
        show(failSave = true)
        compose.onNodeWithContentDescription("Edit alarm").performClick()
        compose.onNodeWithText("Repeat").performClick()
        compose.onNodeWithText("Save alarm").performScrollTo().performClick()
        compose.onNodeWithText("Could not save the alarm. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Repeat").assertIsSelected()
        compose.onNodeWithText("Save alarm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun readinessUsesTheActualMissingAction() {
        show(required = setOf(ReadinessAction.RAISE_ALARM_VOLUME))
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Alarm setup").performClick()
        compose.onNodeWithText("Turn up alarm volume").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, volumeRequests) }
        compose.onNodeWithText("HTTPS verifier endpoint").assertDoesNotExist()
    }
    @Test
    fun setupReturnsToTheDraftAndBackStillLeavesTheEditor() {
        show(required = setOf(ReadinessAction.RAISE_ALARM_VOLUME))
        compose.onNodeWithContentDescription("Edit alarm").performClick()
        compose.onNodeWithText("Repeat").performClick()
        compose.onNodeWithText("Continue alarm setup").performScrollTo().performClick()
        compose.onNodeWithText("Turn up alarm volume").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Done").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Repeat").assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Edit alarm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun anUnchangedSaveCanFinishWithoutAnIntermediateBusyFrame() {
        show()
        compose.onNodeWithContentDescription("Edit alarm").performClick()
        compose.onNodeWithText("Save alarm").performScrollTo().performClick()
        compose.onNodeWithText("Save alarm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun destinationsExposeTheirNamesToAssistiveTechnology() {
        show()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Alarm")).assertExists()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Settings")).assertExists()
        compose.onNode(androidx.compose.ui.test.hasText("Settings") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
    }

    @Test
    fun missingFullScreenAccessOffersTheAlarmScreenRequest() {
        show(required = setOf(ReadinessAction.GRANT_FULL_SCREEN_ACCESS))
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Alarm setup").performClick()
        compose.onAllNodesWithText("Complete").assertCountEquals(0)
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithText("Allow alarm screen").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, fullScreenRequests) }
        compose.onNodeWithText("Done").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsIsATopBarActionAndBackReturnsToTheAlarm() {
        show()
        compose.onNodeWithContentDescription("Alarm navigation").assertDoesNotExist()
        val settings = compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        val actionBounds = settings.fetchSemanticsNode().boundsInRoot
        val alarmBounds = compose.onNodeWithContentDescription("Edit alarm").fetchSemanticsNode().boundsInRoot
        assertTrue("Settings must stay above the alarm content", actionBounds.center.y < alarmBounds.top)
        settings.performClick()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Edit alarm").assertIsDisplayed().performClick()
        compose.onNodeWithText("Repeat").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit alarm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun firstUseChoosesTimeWithoutSavingBeforeTheExplicitReviewAction() {
        show(fresh = true)
        compose.onNodeWithText("Your nights.\nYour mornings.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Alarm navigation").assertDoesNotExist()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Repeat").performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Turn on alarm").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithText("Turn on alarm").performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun openCheckInUsesTheCurrentConnectionCallbacks() {
        show()
        compose.onNodeWithText("Morning check-in").performClick()
        compose.onNodeWithText("Connection details").performClick()
        compose.runOnIdle { callbackGeneration.value = 1 }
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        compose.onNodeWithText("Save setup file").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, verifiedGeneration)
            assertEquals(1, exportedGeneration)
        }
    }

    @Test
    fun replacementDoesNotRenameTheExistingAlarmBeforeAnExplicitSave() {
        show(replacement = true)
        compose.onNodeWithText("Replacement check-in").assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Check-in").performClick()
        compose.onNodeWithText("Replacement check-in").assertExists()
        compose.onNodeWithText("Use for alarm").performScrollTo().performClick()
        compose.onNodeWithText("Save alarm").performScrollTo().assertExists()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test
    fun deferredDraftDoesNotForceSetupOnTheNextLaunch() {
        show(fresh = true, welcome = false, deferredDraft = AlarmSetupDraft(
            "deferred", LocalTime.of(7, 0), LocalDate.of(2026, 10, 21), emptySet(),
        ))
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithText("No alarm set").assertIsDisplayed()
        compose.onNodeWithText("Turn on alarm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, saves) }
    }

}
