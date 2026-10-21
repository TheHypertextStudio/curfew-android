package studio.hypertext.curfew.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class WelcomeAdaptiveInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val postureKind = mutableStateOf(0)
    private val darkMode = mutableStateOf(false)
    private var origin = Offset.Zero
    private var hinge = Rect.Zero
    private var continued = 0

    private fun show(kind: Int) {
        postureKind.value = kind
        compose.setContent {
            val window = LocalWindowInfo.current.containerSize
            val book = postureKind.value == 1
            val bounds = if (book) Rect(window.width / 2f - 12, 0f, window.width / 2f + 12, window.height.toFloat())
                else Rect(0f, window.height / 2f - 12, window.width.toFloat(), window.height / 2f + 12)
            hinge = bounds
            val posture = if (postureKind.value == 0) Posture() else Posture(
                isTabletop = !book,
                hingeList = listOf(HingeInfo(bounds, isFlat = false, isVertical = book, isSeparating = true, isOccluding = true)),
            )
            CurfewTheme(darkTheme = darkMode.value) { Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
                    CurfewWelcomeScreen({ continued++ }, {}, motionEnabled = false, posture = posture)
                    if (postureKind.value != 0) Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color.Black, Offset(bounds.left - origin.x, bounds.top - origin.y), bounds.size)
                    }
                }
            } }
        }
    }

    @Test fun bookPostureGivesEachDisplayContentAndKeepsTheHingeClear() {
        show(1)
        val intro = compose.onNodeWithTag("welcome-introduction").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("welcome-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(intro.right + origin.x <= hinge.left)
        assertTrue(actions.left + origin.x >= hinge.right)
        capture("book-light")
        compose.runOnIdle { darkMode.value = true }
        capture("book-dark")
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, continued) }
    }

    @Test fun tabletopPostureKeepsIntroductionAboveAndActionsBelowTheHinge() {
        show(2)
        val intro = compose.onNodeWithTag("welcome-introduction").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("welcome-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(intro.bottom + origin.y <= hinge.top)
        assertTrue(actions.top + origin.y >= hinge.bottom)
        capture("tabletop-light")
        compose.runOnIdle { darkMode.value = true }
        capture("tabletop-dark")
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, continued) }
    }

    @Test fun changingPostureKeepsOneIntroductionAndOneSetOfActions() {
        show(0)
        for (kind in listOf(1, 2, 0)) {
            compose.runOnIdle { postureKind.value = kind }
            compose.onAllNodesWithText("Your nights.\nYour mornings.").assertCountEquals(1)
            compose.onAllNodesWithText("Get started").assertCountEquals(1)
            compose.onAllNodesWithText("Not now").assertCountEquals(1)
        }
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, continued) }
    }

    @Test fun largerWindowsEmphasizeTheBrandWithoutCrowdingShortPhones() {
        val dimensions = mutableStateOf(320 to 700)
        compose.setContent {
            CurfewTheme { Surface(Modifier.requiredSize(dimensions.value.first.dp, dimensions.value.second.dp)) {
                CurfewWelcomeScreen({}, {}, motionEnabled = false, posture = Posture())
            } }
        }
        fun brandWidth() = compose.onNodeWithText("Curfew").fetchSemanticsNode().size.width.toFloat()
        val phone = brandWidth()
        compose.runOnIdle { dimensions.value = 411 to 900 }
        assertTrue("Roomy portrait phones retain compact branding", brandWidth() <= phone * 1.1f)
        compose.runOnIdle { dimensions.value = 1280 to 800 }
        val tablet = brandWidth()
        assertTrue("A large tablet must give the brand more visual weight", tablet >= phone * 1.5f)
        compose.runOnIdle { dimensions.value = 800 to 320 }
        assertTrue("Short phone windows must retain compact branding", brandWidth() <= phone * 1.1f)
    }

    private fun capture(name: String) {
        val out = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("welcome-posture-$name.png")
        out.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
