package studio.hypertext.curfew.ui

import studio.hypertext.curfew.alarm.AlarmSetupDraft

import androidx.compose.foundation.layout.fillMaxSize
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.readiness.AlarmPresentation
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.ReadinessAction
import studio.hypertext.curfew.ui.theme.CurfewTheme

class CurfewOnboardingInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun setupReadErrorOffersRetryWithoutFreshSetupOrReset() {
        var retries = 0
        compose.setContent { CaptureTheme { FirstUseLoadErrorScreen(onRetry = { retries++ }) } }
        compose.onNodeWithText("Couldn’t load setup").assertIsDisplayed()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("Get started").assertDoesNotExist()
        compose.onNodeWithText("Reset").assertDoesNotExist()
    }

    @Test fun welcomeOffersExplicitSetupAndDeferralWithoutAnAlarmAction() {
        var continued = 0
        var deferred = 0
        compose.setContent {
            CaptureTheme { CurfewWelcomeScreen(onContinue = { continued++ }, onNotNow = { deferred++ }) }
        }
        compose.onNodeWithText("Your nights.\nYour mornings.").assertIsDisplayed()
        val wordmark = compose.onNodeWithText("Curfew").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val introduction = compose.onNodeWithText("Your nights.\nYour mornings.").fetchSemanticsNode().boundsInRoot
        assertTrue("Welcome branding belongs to the centered introduction", kotlin.math.abs(wordmark.center.x - introduction.center.x) < viewport.width * .08f)
        capture("welcome")
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, continued); assertEquals(1, deferred) }
        compose.onNodeWithText("Turn on alarm").assertDoesNotExist()
        compose.onNodeWithText("Sign in").assertDoesNotExist()
    }

    @Test fun reviewDoesNotActivateUntilExplicitChoice() {
        var activations = 0
        compose.setContent {
            CaptureTheme {
                AlarmSetupReview(
                    AlarmSetupDraft("draft-1", java.time.LocalTime.of(7, 0), java.time.LocalDate.of(2026, 10, 21), emptySet()),
                    "Morning check-in", false, null, onActivate = { activations++ },
                )
            }
        }
        compose.runOnIdle { assertEquals(0, activations) }
        compose.onNodeWithText("Morning check-in").assertIsDisplayed()
        capture("review")
        compose.onNodeWithText("Turn on alarm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, activations) }
    }

    @Test fun missingFullScreenAccessShowsItsPurposeAndNoCompletedChecklist() {
        var requests = 0
        compose.setContent {
            CaptureTheme {
                AlarmReadinessScreen(
                    AlarmReadiness(false, setOf(ReadinessAction.GRANT_FULL_SCREEN_ACCESS), emptySet(), AlarmPresentation.LOCK_SCREEN_NOTIFICATION),
                    {}, {}, {}, {}, {}, {}, {}, onGrantFullScreen = { requests++ },
                )
            }
        }
        compose.onNodeWithText("On your lock screen").assertIsDisplayed()
        compose.onNodeWithText("Allow Curfew to appear over your lock screen.").assertIsDisplayed()
        compose.onNodeWithText("Test alarm sound").assertDoesNotExist()
        compose.onNodeWithText("Exact alarms").assertDoesNotExist()
        compose.onNodeWithText("Notifications").assertDoesNotExist()
        capture("full-screen-access")
        compose.onNodeWithText("Allow alarm screen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("On your lock screen").assertIsDisplayed()
    }

    @Test fun liveCapabilityChangeMovesToSoundWithoutClaimingHearing() {
        val required = mutableStateOf(setOf(ReadinessAction.GRANT_FULL_SCREEN_ACCESS, ReadinessAction.PLAY_TEST_SOUND))
        var sounds = 0
        compose.setContent {
            CaptureTheme {
                AlarmReadinessScreen(
                    AlarmReadiness(required.value.isEmpty(), required.value, emptySet(), AlarmPresentation.FULL_SCREEN),
                    {}, {}, {}, { sounds++ }, {}, {}, {}, onGrantFullScreen = {
                        required.value = setOf(ReadinessAction.PLAY_TEST_SOUND)
                    },
                )
            }
        }
        compose.onNodeWithText("Allow alarm screen").performScrollTo().performClick()
        compose.onNodeWithText("Check the sound").assertIsDisplayed()
        capture("sound")
        compose.onNodeWithText("Test alarm sound").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, sounds) }
        compose.onNodeWithText("Check the sound").assertIsDisplayed()
        compose.onNodeWithText("Ready").assertDoesNotExist()
    }
    @androidx.compose.runtime.Composable
    private fun CaptureTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
        CurfewTheme {
            androidx.compose.material3.Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) { content() }
        }
    }

    @Test fun aVerifiedConnectionStillLetsSetupContinueAfterBack() {
        var next = 0
        compose.setContent {
            CaptureTheme {
                CheckInScreen("saved-id", "Morning check-in", true, false, null,
                    { _, _, _ -> }, {}, {}, onDone = { next++ }, inSetup = true)
            }
        }
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, next) }
    }

    private fun capture(scene: String) {
        val output = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("onboarding-$scene.png")
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

}
