package studio.hypertext.curfew.ui

import android.graphics.Bitmap
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class AlarmPickerLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private data class Frame(val time: Boolean = true, val dark: Boolean = false, val generation: Int = 0)
    private val frame = mutableStateOf(Frame())

    @Test fun pickerContentsAndKeyboardActionsFitTheDeviceProfile() {
        var accepted = 0
        compose.setContent {
            val current = frame.value
            key(current.generation) {
                CurfewTheme(darkTheme = current.dark, dynamicColor = false) {
                    if (current.time) AlarmTimePicker(LocalTime.of(7, 0), {}, { accepted++ })
                    else AlarmDatePicker(LocalDate.of(2026, 10, 20), {}, { accepted++ })
                }
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val configuration = context.resources.configuration
        val output = context.cacheDir.resolve("alarm-pickers").apply { deleteRecursively(); mkdirs() }
        output.resolve("profile.txt").writeText("fontScale=${configuration.fontScale} screenWidthDp=${configuration.screenWidthDp}")
        var generation = 0
        for (dark in listOf(false, true)) for (time in listOf(true, false)) {
            compose.runOnIdle { frame.value = Frame(time, dark, ++generation) }
            compose.waitForIdle()
            compose.onNodeWithText("Cancel").assertIsDisplayed()
            compose.onNodeWithText("Done").assertIsDisplayed()
            assertTextFits(configuration.fontScale)
            val name = "${if (time) "time" else "date"}-${if (dark) "dark" else "light"}"
            Thread.sleep(500)
            output.resolve("$name.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (time && compose.onAllNodesWithTag("time-input-mode").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("time-input-mode").performClick()
            }
            if (!time && compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithText("Type date").performClick()
            }
            val field = compose.onAllNodes(hasSetTextAction())[0]
            field.performClick()
            compose.waitForIdle()
            Thread.sleep(500)
            assertTextFits(configuration.fontScale)
            compose.onNodeWithText("Done").assertIsDisplayed().assertIsEnabled()
            output.resolve("$name-keyboard.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val previous = accepted
            compose.onNodeWithText("Done").performTouchInput { click() }
            compose.waitUntil(2_000) { accepted == previous + 1 }
        }
        assertEquals(4, accepted)
    }

    private fun assertTextFits(scale: Float) {
        val texts = compose.onAllNodes(hasText("", substring = true) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        repeat(texts.fetchSemanticsNodes().size) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { layout ->
                assertEquals("The dialog must use the actual device text scale", scale, layout.layoutInput.density.fontScale, 0.01f)
                // Stock text retains a wider paragraph constraint than its tight glyph box.
                assertFalse("Picker text exceeded its line limit", layout.multiParagraph.didExceedMaxLines)
                repeat(layout.lineCount) { line ->
                    assertTrue("Picker glyphs exceed their allocation: text=${layout.layoutInput.text} left=${layout.getLineLeft(line)} right=${layout.getLineRight(line)} bottom=${layout.getLineBottom(line)} constraints=${layout.layoutInput.constraints}", layout.getLineLeft(line) >= -1f &&
                        layout.getLineRight(line) <= layout.layoutInput.constraints.maxWidth + 1f &&
                        layout.getLineBottom(line) <= layout.layoutInput.constraints.maxHeight + 1f)
                }
            }
        }
    }
}
