package studio.hypertext.curfew.ui

import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
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
    fun changingTimeKeepsAnExplicitOneNightDate() {
        var saved: SunriseSchedule? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val initialTime = LocalTime.of(7, 0)
        val chosenDate = today.plusDays(14)
        compose.setContent {
            CurfewTheme(dynamicColor = false) {
                SunriseScheduleEditor(null, "condition-1", true, false, null, { saved = it }, {})
            }
        }
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        compose.onNode(hasText(today.format(format)) or hasText(today.plusDays(1).format(format)))
            .performScrollTo().performClick()
        onView(isAssignableFrom(DatePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
            override fun getDescription() = "Choose a later one-night date"
            override fun perform(controller: UiController, view: View) {
                (view as DatePicker).updateDate(chosenDate.year, chosenDate.monthValue - 1, chosenDate.dayOfMonth)
                controller.loopMainThreadUntilIdle()
            }
        })
        onView(withId(android.R.id.button1)).perform(click())
        compose.onNodeWithText(timeLabel(initialTime, context, locale)).performScrollTo().performClick()
        onView(isAssignableFrom(TimePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
            override fun getDescription() = "Change the wake time"
            override fun perform(controller: UiController, view: View) {
                (view as TimePicker).apply { hour = 8; minute = 15 }
                controller.loopMainThreadUntilIdle()
            }
        })
        onView(withId(android.R.id.button1)).perform(click())
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(LocalTime.of(8, 15), saved?.wakeTime)
            assertEquals("Changing time must preserve the chosen one-night date", chosenDate, saved?.onceDate)
        }
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
