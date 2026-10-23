package studio.hypertext.curfew.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.readiness.AlarmSoundCheckState
import studio.hypertext.curfew.ui.theme.CurfewTheme

class AlarmSoundCheckDialogInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private data class Frame(val dark: Boolean, val state: AlarmSoundCheckState)
    private val frame = mutableStateOf(Frame(false, AlarmSoundCheckState.AWAITING_CONFIRMATION))

    @Test fun backCancelsWhileSoundStarts() {
        var canceled = false
        compose.setContent { CurfewTheme { AlarmSoundCheckDialog(AlarmSoundCheckState.PLAYING, {}, {}, { canceled = true }) } }
        compose.waitForIdle()
        Thread.sleep(500)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(2_000) { canceled }
    }

    @Test fun saveFailureDoesNotDenyAnEarlierConfirmation() {
        compose.setContent { CurfewTheme { AlarmSoundCheckDialog(AlarmSoundCheckState.SAVE_FAILED, {}, {}, {}) } }
        compose.onNodeWithText("Your sound check is still incomplete.").assertDoesNotExist()
    }

    @Test fun confirmationAndFailureDialogsFitBothThemesAndLargeText() {
        var confirmed = 0
        var replayed = 0
        var canceled = 0
        compose.setContent {
            val current = frame.value
            CurfewTheme(darkTheme = current.dark, dynamicColor = false) {
                AlarmSoundCheckDialog(current.state, { confirmed++ }, { replayed++ }, { canceled++ })
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        val scale = configuration.fontScale
        val output = instrumentation.targetContext.cacheDir.resolve("sound-check-dialogs").apply { deleteRecursively(); mkdirs() }
        output.resolve("profile.txt").writeText("fontScale=$scale screenWidthDp=${configuration.screenWidthDp}")
        for (dark in listOf(false, true)) {
            for (state in listOf(AlarmSoundCheckState.AWAITING_CONFIRMATION, AlarmSoundCheckState.FAILED, AlarmSoundCheckState.SAVE_FAILED)) {
                compose.runOnIdle { frame.value = Frame(dark, state) }
                compose.waitForIdle()
                val texts = compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true)
                repeat(texts.fetchSemanticsNodes().size) { index ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    layouts.forEach { layout ->
                        assertEquals("Dialog density must match the device profile", scale, layout.layoutInput.density.fontScale, 0.01f)
                        assertFalse("Dialog text must not clip at scale=$scale state=$state text=${layout.layoutInput.text} size=${layout.size} constraints=${layout.layoutInput.constraints} width=${layout.didOverflowWidth} height=${layout.didOverflowHeight} lines=${layout.lineCount}", layout.hasVisualOverflow)
                    }
                }
                if (state == AlarmSoundCheckState.FAILED) compose.onNodeWithText("I heard it").assertDoesNotExist()
                Thread.sleep(500)
                val name = "${if (dark) "dark" else "light"}-${(scale * 100).toInt()}-${state.name.lowercase()}.png"
                output.resolve(name).outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        compose.onNodeWithText("Try saving again").performClick()
        assertEquals(1, confirmed)
        compose.runOnIdle { frame.value = Frame(false, AlarmSoundCheckState.AWAITING_CONFIRMATION) }
        compose.onNodeWithText("Play again").performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, replayed)
        assertEquals(1, canceled)
    }
}
