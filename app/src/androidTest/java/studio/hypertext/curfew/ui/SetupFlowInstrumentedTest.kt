package studio.hypertext.curfew.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import studio.hypertext.curfew.readiness.*
import studio.hypertext.curfew.ui.theme.CurfewTheme
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred

class SetupFlowInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val required = mutableStateOf<Set<ReadinessAction>>(emptySet())
    private var choices = 0
    private var saves = 0
    private var failDeferral = true
    private val alarm = mutableStateOf<studio.hypertext.curfew.alarm.SavedSunriseAlarm?>(null)
    private val draft = AlarmSetupDraft("draft", LocalTime.of(7, 0), LocalDate.now().plusDays(1), emptySet())

    private fun show(welcome: Boolean = false, resume: Boolean = true, draft: AlarmSetupDraft? = this.draft,
                     failChoice: Boolean = false, verified: Boolean = true, restoration: StateRestorationTester? = null,
                     retryableDeferral: Boolean = false, choiceGate: CompletableDeferred<Unit>? = null) {
        val content: @Composable () -> Unit = {
            CurfewTheme {
                CurfewApp(
                    readiness = AlarmReadiness(required.value.isEmpty(), required.value, emptySet(), AlarmPresentation.FULL_SCREEN),
                    accountEnrolled = false, accountSignedIn = false, accountRecoveryState = null,
                    onGrantExactAlarm = {}, onGrantNotifications = {}, onRepairAlarmChannel = {}, onPlayTestSound = {},
                    onAcknowledgeLimitations = {}, onOpenAccount = {}, onEnrollAccount = {}, onRestoreRecoveryKey = {},
                    onAcknowledgeRecoveryKey = {}, onSaveCallback = { _, _, _ -> "condition-1" }, sunriseAlarm = alarm.value,
                    callbackId = if (verified) "connection" else null, conditionLabel = if (verified) "Morning check-in" else null,
                    alarmBusy = false, alarmError = null, onSaveAlarm = { saves++ }, onToggleAlarm = {},
                    showWelcome = welcome, startSetup = resume, setupDraft = draft, conditionVerified = verified,
                    onWelcomeChoice = { choices++; choiceGate?.await(); if (failChoice || (retryableDeferral && failDeferral)) throw IOException("disk") },
                    onGrantFullScreen = { required.value = emptySet() },
                )
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    @Test fun reviewBackDoesNotBounceThroughCompletedPermissions() {
        required.value = setOf(ReadinessAction.GRANT_FULL_SCREEN_ACCESS)
        show()
        compose.onNodeWithText("Allow alarm screen").performScrollTo().performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Turn on alarm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Turn on alarm").assertDoesNotExist()
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun reviewRetainsItsDraftAfterSavedStateRestoration() {
        val restore = StateRestorationTester(compose)
        show(welcome = true, resume = false, draft = null, restoration = restore)
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Weekly").performScrollTo().performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Turn on alarm").performScrollTo().assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Turn on alarm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Morning check-in").assertExists()
    }

    @Test fun coldResumedReviewCanGoBackToItsWakeTime() {
        show()
        compose.onNodeWithText("Turn on alarm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("One night").performScrollTo().assertIsDisplayed()
    }

    @Test fun failedWelcomeWriteKeepsWelcomeAndAllowsRetry() {
        show(welcome = true, resume = false, draft = null, failChoice = true)
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Your nights.\nYour mornings.").assertExists()
        compose.onNodeWithText("Couldn’t save setup. Try again.").assertExists()
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, choices) }
    }

    @Test fun checkInStartsWithPurposeAndCanDeferWithoutAnAlarm() {
        show(verified = false)
        compose.onNodeWithText("Service address").assertDoesNotExist()
        compose.onNodeWithText("I have a service").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save for later").performScrollTo().performClick()
        compose.onNodeWithText("No alarm set").assertExists()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun failedDeferralKeepsDraftAndOffersAVisibleRetry() {
        show(verified = false, retryableDeferral = true)
        compose.onNodeWithText("Save for later").performScrollTo().performClick()
        compose.onNodeWithText("Couldn’t save setup. Try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save for later").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, saves); failDeferral = false }
        compose.onNodeWithText("Save for later").performScrollTo().performClick()
        compose.onNodeWithText("No alarm set").assertExists()
        compose.onNodeWithText("Finish setup").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("One night").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, choices); assertEquals(0, saves) }
    }

    @Test fun savingDeferralDisablesTheCompetingServiceAction() {
        val gate = CompletableDeferred<Unit>()
        show(verified = false, choiceGate = gate)
        compose.onNodeWithText("Save for later").performScrollTo().performClick()
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.onNodeWithText("I have a service").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, choices); gate.complete(Unit) }
        compose.onNodeWithText("No alarm set").assertExists()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun backFromManualConnectionReturnsToItsIntroduction() {
        show(verified = false)
        compose.onNodeWithText("I have a service").performScrollTo().performClick()
        compose.onNodeWithText("Service address").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("I have a service").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Service address").assertDoesNotExist()
    }

    @Test fun restoredPendingActivationCanRetryInsteadOfStayingBusy() {
        val restore = StateRestorationTester(compose)
        show(restoration = restore)
        compose.onNodeWithText("Turn on alarm").performScrollTo().performClick()
        compose.onNodeWithText("Turning on…").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Turn on alarm").performScrollTo().assertIsEnabled()
    }

    @Test fun activationCommittedAfterRestorationReconcilesToDaily() {
        val restore = StateRestorationTester(compose)
        show(restoration = restore)
        compose.onNodeWithText("Turn on alarm").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            alarm.value = studio.hypertext.curfew.alarm.SavedSunriseAlarm(draft.toSchedule("connection"), null)
        }
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithText("Turn on alarm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun returningFromPermissionsInSettingsDoesNotOpenCheckIn() {
        show(resume = false, draft = null, verified = false)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Alarm setup").performClick()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("Account").assertExists()
        compose.onNodeWithText("I have a service").assertDoesNotExist()
    }
}
