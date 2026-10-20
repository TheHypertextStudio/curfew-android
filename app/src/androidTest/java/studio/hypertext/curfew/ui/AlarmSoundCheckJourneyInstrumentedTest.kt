package studio.hypertext.curfew.ui

import android.media.AudioAttributes
import android.media.AudioManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.persistence.CurfewPreferences

class AlarmSoundCheckJourneyInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = CurfewPreferences(context)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var originalVolume = 0

    @Before fun prepare(): Unit = runBlocking {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
            "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow",
        )) automation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        if (android.os.Build.VERSION.SDK_INT >= 34) automation.executeShellCommand(
            "appops set ${context.packageName} USE_FULL_SCREEN_INTENT allow",
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        studio.hypertext.curfew.persistence.FirstUsePreferences(context).chooseNotNow()
        originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, maxOf(originalVolume, 1), 0)
        preferences.setTestSoundConfirmed(false)
        preferences.acknowledgeLimitations()
        compose.activityRule.scenario.recreate()
    }

    @After fun restore() = runBlocking {
        audio.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
        preferences.setTestSoundConfirmed(false)
    }

    private fun awaitConfirmationPrompt() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Did you hear the alarm?").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Did you hear the alarm?").assertIsDisplayed()
    }

    private fun openAndPlay() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Alarm setup").performClick()
        compose.onNodeWithText("Test alarm sound").performScrollTo().performClick()
        compose.waitUntil(5_000) { audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM } }
        awaitConfirmationPrompt()
        assertFalse(runBlocking { preferences.state.first().testSoundCompleted })
        Thread.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = context.cacheDir.resolve("sound-check-native.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun setupExplainsTheSoundCheckPurpose() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Alarm setup").performClick()
        compose.onNodeWithText("Check the sound").assertIsDisplayed()
        compose.onNodeWithText("Test alarm sound").assertIsDisplayed()
    }

    @Test fun playbackNeedsConfirmationAndRemainsAvailableAfterward() {
        openAndPlay()
        compose.onNodeWithText("I heard it").performClick()
        compose.waitUntil(5_000) { runBlocking { preferences.state.first().testSoundCompleted } }
        compose.onNodeWithText("Did you hear the alarm?").assertDoesNotExist()
        compose.onNodeWithText("Test alarm sound").performScrollTo().performClick()
        awaitConfirmationPrompt()
        compose.onNodeWithText("Not now").performClick()
        assertTrue(runBlocking { preferences.state.first().testSoundCompleted })
    }

    @Test fun cancelingDoesNotCompleteSetupAndStopsPreview() {
        openAndPlay()
        compose.onNodeWithText("Not now").performClick()
        compose.waitUntil(5_000) { audio.activePlaybackConfigurations.none { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM } }
        assertFalse(runBlocking { preferences.state.first().testSoundCompleted })
        compose.onNodeWithText("Test alarm sound").assertIsDisplayed()
    }

    @Test fun leavingTheAppCannotConfirmOrKeepPlaying() {
        openAndPlay()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(5_000) { audio.activePlaybackConfigurations.none { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM } }
        assertFalse(runBlocking { preferences.state.first().testSoundCompleted })
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Did you hear the alarm?").assertDoesNotExist()
    }
}
