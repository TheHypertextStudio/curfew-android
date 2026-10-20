package studio.hypertext.curfew.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.platform.testTag
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
import java.time.ZoneId
import studio.hypertext.curfew.alarm.SunriseOccurrence
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.ui.theme.CurfewTheme

class SunriseDailyLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val scale = mutableFloatStateOf(1f)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = context.resources.configuration.locales[0]
    private val wakeTime = LocalTime.of(7, 0)

    private fun show(fontScale: Float, nextDate: LocalDate? = null, onceDate: LocalDate? = null, width: Int = 320, height: Int? = null, canArm: Boolean = true) {
        scale.floatValue = fontScale
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                CurfewTheme(dynamicColor = false) {
                    Box(Modifier.testTag("daily-fixture").width(width.dp).then(if (height == null) Modifier.fillMaxHeight() else Modifier.height(height.dp))) {
                        SunriseDailyScreen(
                            alarm = SavedSunriseAlarm(SunriseSchedule("layout-alarm", wakeTime, onceDate, if (onceDate == null) DayOfWeek.entries.take(5).toSet() else emptySet(), true, "check-in"),
                                nextDate?.let { SunriseOccurrence("layout-occurrence", it, it.atTime(wakeTime).atZone(ZoneId.systemDefault()).toInstant()) }),
                            conditionLabel = "Morning check-in", conditionVerified = true,
                            canArm = canArm, busy = false, error = null,
                            onEdit = {}, onCheckIn = {}, onSetup = {}, onToggle = {},
                        )
                    }
                }
            }
        }
    }

    @Test fun enabledAlarmReportsAttentionWhenReadinessIsLost() {
        show(1f, width = 390, canArm = false)
        compose.onNodeWithText("Needs attention").assertExists()
        compose.onNodeWithText("Alarm on").assertDoesNotExist()
        compose.onNodeWithText("Finish setup").assertExists()
    }

    @Test fun ordinaryAlarmDoesNotSpendARowRepeatingItsSwitchState() {
        show(1f, width = 390)
        compose.onNodeWithText("Alarm on").assertDoesNotExist()
        val toggle = compose.onNodeWithContentDescription("Enable future Sunrise alarms").fetchSemanticsNode().boundsInRoot
        val numeric = wakeTime.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale))
        val time = compose.onNodeWithText(numeric, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The switch belongs beside the time", toggle.center.y in time.top..time.bottom)
        val checkIn = compose.onNodeWithText("Morning check-in").fetchSemanticsNode().boundsInRoot
        val fixtureTop = compose.onNodeWithTag("daily-fixture").fetchSemanticsNode().boundsInRoot.top
        val heightDp = (checkIn.bottom - fixtureTop) / context.resources.displayMetrics.density
        assertTrue("The ordinary alarm and check-in must fit within 280dp: height=$heightDp time=$time checkIn=$checkIn fixtureTop=$fixtureTop", heightDp <= 280f)
    }

    @Test fun ordinaryPhoneGivesTheWakeTimeVisualPriority() {
        show(1f, width = 390)
        val numeric = wakeTime.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale))
        val time = compose.onNodeWithText(numeric, useUnmergedTree = true).textLayout()
        val status = weekday(DayOfWeek.MONDAY).textLayout()
        assertTrue("The chosen time must lead the single-alarm screen",
            time.layoutInput.style.fontSize.value >= 5f * status.layoutInput.style.fontSize.value)
    }

    @Test fun enlargedTextKeepsTheDailyGroupAtTheTop() {
        show(2f)
        val start = compose.onNodeWithContentDescription("Edit Sunrise").fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("Enlarged text must start near the top instead of centering a long task", start.top - compose.onNodeWithTag("daily-fixture").fetchSemanticsNode().boundsInRoot.top <= 40f * density)
    }

    @Test fun shortPhoneKeepsTheDailyGroupAtTheTop() {
        show(1f, width = 390, height = 360)
        val start = compose.onNodeWithContentDescription("Edit Sunrise").fetchSemanticsNode().boundsInRoot
        assertTrue("The editable time must fit within the initial 56dp of the short task: $start",
            start.top - compose.onNodeWithTag("daily-fixture").fetchSemanticsNode().boundsInRoot.top <= 56f * context.resources.displayMetrics.density)
    }

    @Test fun weekdaysRemainSeparatedAtNarrowWidthAndLargeText() {
        show(2f)
        val markers = DayOfWeek.entries.map(::markerBounds)
        markers.zipWithNext().forEach { (left, right) ->
            assertTrue("Weekday markers must have a visible gap: $left / $right", right.left - left.right >= 1f)
        }
        DayOfWeek.entries.forEach { day ->
            val layout = weekday(day).textLayout()
            assertFalse("The weekday label must not clip: day=$day marker=${markerBounds(day)} ${layout.diagnostics()}", layout.hasVisualOverflow)
        }
    }

    @Test fun weekdayMarkersGrowWithLargeText() {
        show(1f)
        val normalHeight = markerBounds(DayOfWeek.MONDAY).height
        compose.runOnIdle { scale.floatValue = 2f }
        compose.waitForIdle()
        val largeHeight = markerBounds(DayOfWeek.MONDAY).height
        assertTrue("The marker must grow around the larger label: $normalHeight -> $largeHeight", largeHeight > normalHeight)
    }

    @Test fun numericTimeKeepsHierarchyWithoutClippingAtLargeText() {
        show(2f)
        val twentyFour = DateFormat.is24HourFormat(context)
        val numeric = wakeTime.format(DateTimeFormatter.ofPattern(if (twentyFour) "HH:mm" else "h:mm", locale))
        val numericLayout = compose.onNodeWithText(numeric, useUnmergedTree = true).textLayout()
        val statusLayout = weekday(DayOfWeek.MONDAY).textLayout()
        assertFalse("The numeric time must not clip: ${numericLayout.diagnostics()}", numericLayout.hasVisualOverflow)
        assertTrue("The time must remain larger than a day label", numericLayout.layoutInput.style.fontSize.value >= statusLayout.layoutInput.style.fontSize.value * 1.5f)
        if (!twentyFour) {
            val period = wakeTime.format(DateTimeFormatter.ofPattern("a", locale))
            val periodLayout = compose.onNodeWithText(period, useUnmergedTree = true).textLayout()
            assertFalse("The period must not clip: ${periodLayout.diagnostics()}", periodLayout.hasVisualOverflow)
            assertTrue("The period must not compete with the numeric time", numericLayout.layoutInput.style.fontSize.value >= periodLayout.layoutInput.style.fontSize.value * 1.5f)
        }
    }

    @Test fun weeklyAlarmDoesNotRepeatTodayBelowItsDays() {
        show(1f, LocalDate.now(ZoneId.systemDefault()))
        compose.onNodeWithText("Today").assertDoesNotExist()
    }

    @Test fun weeklyAlarmDoesNotRepeatTomorrowBelowItsDays() {
        show(1f, LocalDate.now(ZoneId.systemDefault()).plusDays(1))
        compose.onNodeWithText("Tomorrow").assertDoesNotExist()
    }

    @Test fun narrowTimeKeepsPeriodInlineWhenItFits() {
        if (DateFormat.is24HourFormat(context)) return
        show(1f)
        val numeric = compose.onNodeWithText(wakeTime.format(DateTimeFormatter.ofPattern("h:mm", locale)), useUnmergedTree = true)
        val period = compose.onNodeWithText(wakeTime.format(DateTimeFormatter.ofPattern("a", locale)), useUnmergedTree = true)
        val numericBaseline = numeric.fetchSemanticsNode().boundsInRoot.top + numeric.textLayout().firstBaseline
        val periodBaseline = period.fetchSemanticsNode().boundsInRoot.top + period.textLayout().firstBaseline
        assertEquals("AM/PM must share the time baseline when it fits", numericBaseline, periodBaseline, 1f)
    }

    @Test fun oneNightAlarmRetainsItsChosenDate() {
        val date = LocalDate.now(ZoneId.systemDefault()).plusDays(3)
        show(1f, nextDate = date, onceDate = date)
        compose.onNodeWithText(date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale))).assertExists()
    }

    @Test fun scheduleAndCheckInStayVisuallyGrouped() {
        show(1f, LocalDate.now(ZoneId.systemDefault()))
        val dayBottom = markerBounds(DayOfWeek.MONDAY).bottom
        val checkInTop = compose.onNodeWithText("Morning check-in").fetchSemanticsNode().boundsInRoot.top
        val density = context.resources.displayMetrics.density
        assertTrue("The check-in must stay within 72dp of the repeat days", checkInTop - dayBottom <= 72f * density)
    }

    @Test fun timeFormattingPreservesClockPreferenceAndLocalizedPeriod() {
        val twentyFour = DateFormat.is24HourFormat(context)
        for (testLocale in listOf(Locale.US, Locale.GERMANY, Locale.JAPAN)) {
            for (time in listOf(LocalTime.MIDNIGHT, LocalTime.NOON, LocalTime.of(23, 5))) {
                val expected = time.format(DateTimeFormatter.ofPattern(if (twentyFour) "HH:mm" else "h:mm a", testLocale))
                assertEquals(expected, timeLabel(time, context, testLocale))
            }
        }
    }

    private fun weekday(day: DayOfWeek): SemanticsNodeInteraction = compose.onNodeWithContentDescription(
        "${day.getDisplayName(TextStyle.FULL, locale)}, ${if (day.value <= 5) "selected" else "not selected"}",
        useUnmergedTree = true,
    )

    private fun markerBounds(day: DayOfWeek): Rect = requireNotNull(weekday(day).fetchSemanticsNode().layoutInfo.parentInfo).coordinates.boundsInRoot()

    private fun TextLayoutResult.diagnostics(): String = buildString {
        append("text=${layoutInput.text.text} widthOverflow=$didOverflowWidth heightOverflow=$didOverflowHeight ")
        append("constraints=${layoutInput.constraints} size=$size fontSize=${layoutInput.style.fontSize} ")
        append("lineHeight=${layoutInput.style.lineHeight} density=${layoutInput.density.density} fontScale=${layoutInput.density.fontScale} ")
        append("lineCount=$lineCount maxLines=${layoutInput.maxLines} softWrap=${layoutInput.softWrap}")
        for (line in 0 until lineCount) {
            append(" line$line(left=${getLineLeft(line)},right=${getLineRight(line)},top=${getLineTop(line)},bottom=${getLineBottom(line)},ellipsized=${isLineEllipsized(line)})")
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
