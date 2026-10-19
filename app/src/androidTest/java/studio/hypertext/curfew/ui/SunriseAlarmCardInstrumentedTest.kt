package studio.hypertext.curfew.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
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

class SunriseAlarmCardInstrumentedTest {
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
                SunriseAlarmCard(null, null, null, false, false, {}, {})
            }
        }

        compose.onNodeWithText("Save Sunrise").assertIsNotEnabled()
        compose.onNodeWithText("Configure and test a wake condition before turning Sunrise on.")
            .assertTextContains("Configure and test", substring = true)
    }

    @Test
    fun editingTheSavedAlarmPreservesItsIdentityAndOneNightDate() {
        var saved: SunriseSchedule? = null
        compose.setContent {
            CurfewTheme(dynamicColor = false) {
                SunriseAlarmCard(alarm, "condition-1", "Start the day", true, false, { saved = it }, {})
            }
        }

        compose.onNodeWithText("Save Sunrise").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(schedule, saved) }
    }

    @Test
    fun weeklySelectionDoesNotRetainTheOneNightDate() {
        var saved: SunriseSchedule? = null
        compose.setContent {
            CurfewTheme(dynamicColor = false) {
                SunriseAlarmCard(alarm, "condition-1", "Start the day", true, false, { saved = it }, {})
            }
        }

        compose.onNodeWithText("Repeat weekly").performClick()
        compose.onNodeWithText("Save Sunrise").performClick()

        compose.runOnIdle {
            assertEquals(null, saved?.onceDate)
            assertEquals(DayOfWeek.entries.toSet(), saved?.weekdays)
        }
    }
}
