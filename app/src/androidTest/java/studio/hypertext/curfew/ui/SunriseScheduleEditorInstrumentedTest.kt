package studio.hypertext.curfew.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
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
            EditorFixture {
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
            EditorFixture {
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
            EditorFixture {
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
        confirmTime(8, 15)
        compose.onNodeWithText(chosenDate.format(format)).performScrollTo().assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureDateDraft") == "true") {
            instrumentation.uiAutomation.takeScreenshot()?.let { image ->
                context.cacheDir.resolve("one-night-date-draft.png").outputStream().use {
                    image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(LocalTime.of(8, 15), saved?.wakeTime)
            assertEquals("Changing time must preserve the chosen one-night date", chosenDate, saved?.onceDate)
        }
    }

    @Test
    fun changingTimeStillUpdatesAnInferredOneNightDate() {
        var saved: SunriseSchedule? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        compose.setContent {
            EditorFixture {
                SunriseScheduleEditor(null, "condition-1", true, false, null, { saved = it }, {})
            }
        }
        compose.onNodeWithText(timeLabel(LocalTime.of(7, 0), context, locale)).performScrollTo().performClick()
        confirmTime(0, 0)
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(LocalTime.MIDNIGHT, saved?.wakeTime)
            assertEquals(LocalDate.now(ZoneId.systemDefault()).plusDays(1), saved?.onceDate)
        }
    }

    @Composable
    private fun EditorFixture(content: @Composable () -> Unit) {
        val activity = LocalActivity.current
        SideEffect {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
        CurfewTheme(darkTheme = false, dynamicColor = false) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) { content() }
            }
        }
    }

    private fun confirmTime(hour: Int, minute: Int) {
        onView(isAssignableFrom(TimePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
            override fun getDescription() = "Change the wake time"
            override fun perform(controller: UiController, view: View) {
                val picker = view as TimePicker
                picker.hour = hour
                picker.minute = minute
                controller.loopMainThreadUntilIdle()
            }
        })
        onView(withId(android.R.id.button1)).perform(click())
    }

    @Test
    fun weeklySelectionDoesNotRetainTheOneNightDate() {
        var saved: SunriseSchedule? = null
        compose.setContent {
            EditorFixture {
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
