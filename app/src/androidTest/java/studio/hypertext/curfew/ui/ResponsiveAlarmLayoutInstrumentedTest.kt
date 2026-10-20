package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.ui.theme.CurfewTheme

class ResponsiveAlarmLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val alarm = SavedSunriseAlarm(SunriseSchedule("responsive", LocalTime.of(7, 0), null, DayOfWeek.entries.toSet(), false, "check-in"), null)

    @Test fun repeatChoicesStayReadableInAScaledNarrowWindow() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                CurfewTheme(dynamicColor = false) {
                    Box(Modifier.width(240.dp).height(400.dp)) {
                        SunriseScheduleEditor(alarm, "check-in", true, false, null, {}, {})
                    }
                }
            }
        }
        for (label in listOf("One night", "Weekly")) {
            val node = compose.onNodeWithText(label).performScrollTo()
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            // Intrinsic text measurement can retain a paragraph wider than its glyph box.
            assertEquals("$label must stay on one readable line", 1, layout.lineCount)
            assertTrue("$label glyphs must fit: right=${layout.getLineRight(0)} size=${layout.size}", layout.getLineLeft(0) >= -1f && layout.getLineRight(0) <= layout.size.width + 1f)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("$label must fit inside its window: $bounds / $root", bounds.right <= root.right && bounds.left >= root.left)
        }
        compose.onNodeWithText("Save").performScrollTo().assertIsDisplayed()
    }
}
