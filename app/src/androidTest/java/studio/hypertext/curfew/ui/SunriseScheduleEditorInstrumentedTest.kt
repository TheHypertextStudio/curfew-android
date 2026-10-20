package studio.hypertext.curfew.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseOccurrence
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.ui.theme.CurfewTheme

class SunriseScheduleEditorInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()
    private val schedule = SunriseSchedule(
        "sunrise-1", LocalTime.of(7, 0), LocalDate.of(2026, 10, 20), emptySet(), true, "condition-1",
    )
    private val alarm = SavedSunriseAlarm(schedule, SunriseOccurrence(
        "morning-1", LocalDate.of(2026, 10, 20), Instant.parse("2026-10-20T14:00:00Z"),
    ))

    @Test
    fun savingRequiresAConfiguredReleaseCondition() {
        compose.setContent {
            CurfewTheme(dynamicColor = false) {
                SunriseScheduleEditor(null, null, false, false, null, {}, {})
            }
        }

        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Finish setup").assertIsDisplayed()
    }

    @Test
    fun editingTheSavedAlarmPreservesItsIdentityAndOneNightDate() {
        var saved: SunriseSchedule? = null
        compose.setContent {
            CurfewTheme(dynamicColor = false) {
                SunriseScheduleEditor(alarm, "condition-1", true, false, null, { saved = it }, {})
            }
        }

        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(schedule, saved) }
    }

    @Test
    fun weeklySelectionDoesNotRetainTheOneNightDate() {
        var saved: SunriseSchedule? = null
        compose.setContent {
            CurfewTheme(dynamicColor = false) {
                SunriseScheduleEditor(alarm, "condition-1", true, false, null, { saved = it }, {})
            }
        }

        compose.onNodeWithText("Weekly").performClick()
        compose.onNodeWithText("Save").performClick()

        compose.runOnIdle {
            assertEquals(null, saved?.onceDate)
            assertEquals(DayOfWeek.entries.toSet(), saved?.weekdays)
        }
    }
}
