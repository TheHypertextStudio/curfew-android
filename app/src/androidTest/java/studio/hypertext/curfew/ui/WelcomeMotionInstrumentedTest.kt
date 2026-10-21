package studio.hypertext.curfew.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class WelcomeMotionInstrumentedTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test fun entranceMovesIntoPlaceWithoutBlockingSetup() {
        compose.mainClock.autoAdvance = false
        var setups = 0
        compose.setContent {
            CurfewTheme { Surface(Modifier.fillMaxSize()) {
                CurfewWelcomeScreen({ setups++ }, {}, motionEnabled = true)
            } }
        }
        val start = compose.onNodeWithText("Your nights.\nYour mornings.").fetchSemanticsNode().boundsInRoot.top
        var elapsed = 0L
        for (time in listOf(0L, 80L, 180L, 320L, 600L, 1000L)) {
            compose.mainClock.advanceTimeBy(time - elapsed)
            elapsed = time
            capture("entrance-$time")
            if (time == 180L) compose.onNodeWithText("Get started").performClick()
        }
        val end = compose.onNodeWithText("Your nights.\nYour mornings.").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Headline must settle upward into its final position", start > end)
        compose.runOnIdle { assertEquals(1, setups) }
    }

    @Test fun reducedMotionShowsTheFinalCompositionImmediately() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CurfewTheme { Surface(Modifier.fillMaxSize()) {
                CurfewWelcomeScreen({}, {}, motionEnabled = false)
            } }
        }
        val start = compose.onNodeWithText("Your nights.\nYour mornings.").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(1000)
        val end = compose.onNodeWithText("Your nights.\nYour mornings.").fetchSemanticsNode().boundsInRoot
        assertEquals(start, end)
        compose.onNodeWithText("Get started").performClick()
    }

    private fun capture(name: String) {
        val output = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("$name.png")
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
