package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class SetupPostureInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var origin = Offset.Zero
    private var hinge = Rect.Zero
    private fun show(book: Boolean) {
        compose.setContent {
            val window = LocalWindowInfo.current.containerSize
            hinge = if (book) Rect(window.width / 2f - 12, 0f, window.width / 2f + 12, window.height.toFloat())
                else Rect(0f, window.height / 2f - 12, window.width.toFloat(), window.height / 2f + 12)
            val posture = Posture(isTabletop = !book,
                hingeList = listOf(HingeInfo(hinge, isFlat = false, isVertical = book, isSeparating = true, isOccluding = true)))
            CurfewTheme { Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
                    SetupTaskContent(posture = posture,
                        introduction = { Text("Your check-in", style = MaterialTheme.typography.headlineMedium) }) {
                        Button(onClick = {}) { Text("Continue") }
                    }
                }
            } }
        }
    }
    @Test fun bookKeepsPurposeAndActionOnSeparateDisplays() {
        show(true)
        val purpose = compose.onNodeWithTag("setup-purpose").fetchSemanticsNode().boundsInRoot
        val work = compose.onNodeWithTag("setup-work").fetchSemanticsNode().boundsInRoot
        assertTrue(purpose.right + origin.x <= hinge.left)
        assertTrue(work.left + origin.x >= hinge.right)
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
    }
    @Test fun tabletopKeepsBothRegionsClearOfTheHinge() {
        show(false)
        val purpose = compose.onNodeWithTag("setup-purpose").fetchSemanticsNode().boundsInRoot
        val work = compose.onNodeWithTag("setup-work").fetchSemanticsNode().boundsInRoot
        assertTrue(purpose.bottom + origin.y <= hinge.top)
        assertTrue(work.top + origin.y >= hinge.bottom)
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
    }
}
