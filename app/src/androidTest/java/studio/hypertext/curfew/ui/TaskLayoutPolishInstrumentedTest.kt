package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.readiness.AlarmPresentation
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.ReadinessAction
import studio.hypertext.curfew.ui.theme.CurfewTheme

class TaskLayoutPolishInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val schedule = SunriseSchedule("layout", LocalTime.of(7, 0), LocalDate.of(2026, 10, 20), emptySet(), true, "check-in")

    @Test fun shortDailyViewportShowsTheWholeCheckInRow() {
        compose.setContent {
            CurfewTheme {
                Box(Modifier.size(390.dp, 235.dp).testTag("viewport")) {
                    SunriseDailyScreen(SavedSunriseAlarm(schedule, null), "Morning check-in", true, true, false, null, {}, {}, {}, {})
                }
            }
        }
        val row = compose.onNodeWithText("Morning check-in").fetchSemanticsNode().layoutInfo.coordinates
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("The entire check-in row must fit without scrolling in a short window", row.positionInRoot().y + row.size.height <= viewport.bottom + 1f)
    }

    @Test fun narrowRepeatControlsDoNotLeaveSundayAlone() {
        val locale = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]
        compose.setContent {
            CurfewTheme {
                Box(Modifier.size(360.dp, 600.dp)) {
                    SunriseScheduleEditor(SavedSunriseAlarm(schedule.copy(onceDate = null, weekdays = DayOfWeek.entries.toSet()), null), "check-in", true, false, null, {}, {})
                }
            }
        }
        val rows = DayOfWeek.entries.map { day ->
            compose.onNodeWithContentDescription("Repeat on ${day.getDisplayName(TextStyle.FULL, locale)}")
                .fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().y
        }.groupingBy { it.toInt() }.eachCount().values
        assertTrue("A week must not end in an isolated day: $rows", rows.all { it > 1 })
    }

    @Test fun enlargedPermissionButtonCentersEveryLine() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                CurfewTheme {
                    Box(Modifier.size(320.dp, 650.dp)) {
                        AlarmReadinessScreen(
                            AlarmReadiness(false, setOf(ReadinessAction.GRANT_FULL_SCREEN_ACCESS), emptySet(), AlarmPresentation.FULL_SCREEN),
                            {}, {}, {}, {}, {}, {}, {}, {},
                        )
                    }
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Allow alarm screen", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertTrue("This fixture must exercise a wrapped label", layout.lineCount > 1)
        for (line in 0 until layout.lineCount) {
            assertEquals("Every permission-label line must share the button's center", layout.size.width / 2f,
                (layout.getLineLeft(line) + layout.getLineRight(line)) / 2f, 1f)
        }
    }
}
