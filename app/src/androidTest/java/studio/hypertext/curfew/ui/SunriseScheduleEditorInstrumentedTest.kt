package studio.hypertext.curfew.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseOccurrence
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.ui.theme.CurfewTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SunriseScheduleEditorInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()
    private val schedule =
        SunriseSchedule(
            "sunrise-1",
            LocalTime.of(7, 0),
            LocalDate.of(2026, 10, 20),
            emptySet(),
            true,
            "condition-1",
        )
    private val alarm =
        SavedSunriseAlarm(
            schedule,
            SunriseOccurrence(
                "morning-1",
                LocalDate.of(2026, 10, 20),
                Instant.parse("2026-10-20T14:00:00Z"),
            ),
        )

    private fun timeControl(time: LocalTime): SemanticsNodeInteraction {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        val numeric =
            time.format(
                DateTimeFormatter.ofPattern(
                    if (android.text.format.DateFormat
                            .is24HourFormat(context)
                    ) {
                        "HH:mm"
                    } else {
                        "h:mm"
                    },
                    locale,
                ),
            )
        return compose.onNode(hasText(numeric) and hasClickAction())
    }

    @Test
    fun savingRequiresAConfiguredReleaseCondition() {
        var saves = 0
        var setup = 0
        compose.setContent {
            EditorFixture {
                SunriseScheduleEditor(null, null, false, false, null, { saves++ }, { setup++ })
            }
        }

        compose.onNodeWithText("Save alarm").assertDoesNotExist()
        compose
            .onNodeWithText("Continue alarm setup")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle {
            assertEquals(0, saves)
            assertEquals(1, setup)
        }
    }

    @Test
    fun editingTheSavedAlarmPreservesItsIdentityAndOneNightDate() {
        var saved: SunriseSchedule? = null
        compose.setContent {
            EditorFixture {
                SunriseScheduleEditor(alarm, "condition-1", true, false, null, { saved = it }, {})
            }
        }

        compose
            .onNodeWithText("Save alarm")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle { assertEquals(schedule, saved) }
    }

    @Test
    fun timePickerUsesThemedDoneAndCancelActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            EditorFixture { SunriseScheduleEditor(alarm, "condition-1", true, false, null, {}, {}) }
        }
        timeControl(schedule.wakeTime)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.onNodeWithText("Save alarm").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun datePickerUsesThemedDoneAndCancelActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            EditorFixture { SunriseScheduleEditor(alarm, "condition-1", true, false, null, {}, {}) }
        }
        compose
            .onNodeWithText(
                schedule.onceDate!!.format(
                    DateTimeFormatter
                        .ofLocalizedDate(FormatStyle.FULL)
                        .withLocale(context.resources.configuration.locales[0]),
                ),
            ).performScrollTo()
            .performClick()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.onNodeWithText("Save alarm").performScrollTo().assertIsDisplayed()
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
        compose
            .onNode(hasText(today.format(format)) or hasText(today.plusDays(1).format(format)))
            .performScrollTo()
            .performClick()
        enterDate(chosenDate)
        compose.onNodeWithText("Done").assertIsEnabled().performClick()
        awaitEditorWindow()
        compose.onNodeWithText(chosenDate.format(format)).performScrollTo().assertIsDisplayed()
        timeControl(initialTime).performScrollTo().performClick()
        confirmTime(8, 15)
        awaitEditorWindow()
        compose.onNodeWithText(chosenDate.format(format)).performScrollTo().assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureDateDraft") == "true") {
            instrumentation.uiAutomation.takeScreenshot()?.let { image ->
                context.cacheDir.resolve("one-night-date-draft.png").outputStream().use {
                    image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        saveEditor()
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
        timeControl(LocalTime.of(7, 0)).performScrollTo().performClick()
        confirmTime(0, 0)
        saveEditor()
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

    private fun saveEditor() {
        compose.onNodeWithText("Save alarm").performScrollTo()
        awaitEditorWindow()
        compose
            .onNodeWithText("Save alarm")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
    }

    private fun awaitEditorWindow() {
        // Dialog semantics can disappear before the platform window and IME finish
        // dismissing. Wait for the actual editor window before injecting one tap.
        compose.waitUntil(5_000) {
            var ready = false
            compose.runOnUiThread {
                val roots =
                    android.view.inspector.WindowInspector
                        .getGlobalWindowViews()
                        .filter { it.isAttachedToWindow && it.visibility == android.view.View.VISIBLE }
                ready = roots.singleOrNull()?.let { root ->
                    val insets =
                        androidx.core.view.ViewCompat
                            .getRootWindowInsets(root)
                    root.isLaidOut && !root.isLayoutRequested && !root.isInLayout &&
                        insets
                            ?.getInsets(
                                androidx.core.view.WindowInsetsCompat.Type
                                    .ime(),
                            )?.bottom == 0
                } == true
            }
            ready
        }
    }

    private fun openDateInput() {
        if (compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            compose.onNodeWithText("Type date").performClick()
        }
    }

    @Test fun ordinaryPhoneStartsWithTheCalendar() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(
            "A calendar requires the ordinary-size device profile",
            context.resources.configuration.screenWidthDp >= 384 && context.resources.configuration.fontScale <= 1f,
        )
        compose.setContent { EditorFixture { AlarmDatePicker(LocalDate.of(2026, 10, 20), {}, {}) } }
        compose.onNodeWithText("Type date").assertIsDisplayed()
    }

    private fun enterDate(date: LocalDate) {
        openDateInput()
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        val order =
            android.text.format.DateFormat
                .getBestDateTimePattern(locale, "yMd")
                .filter { it in "yMd" }
                .toList()
                .distinct()
        val digits =
            order.joinToString("") { part ->
                date.format(
                    DateTimeFormatter.ofPattern(
                        when (part) {
                            'y' -> "yyyy"
                            'M' -> "MM"
                            else -> "dd"
                        },
                        locale,
                    ),
                )
            }
        val input = compose.onAllNodes(hasSetTextAction())[0]
        focusPickerInput(input)
        input.performTextReplacement(digits)
    }

    private fun focusPickerInput(input: SemanticsNodeInteraction) {
        input.performClick().assertIsFocused()
        // A text field can be ready in semantics before its IME moves the dialog.
        // Establish the focused native input window before replacing text or
        // injecting the next picker tap.
        compose.waitUntil(5_000) {
            var ready = false
            compose.runOnUiThread {
                val focused =
                    android.view.inspector.WindowInspector
                        .getGlobalWindowViews()
                        .singleOrNull { it.isAttachedToWindow && it.visibility == android.view.View.VISIBLE && it.hasWindowFocus() }
                val insets = focused?.let(androidx.core.view.ViewCompat::getRootWindowInsets)
                ready = focused != null && focused.isLaidOut && !focused.isLayoutRequested && !focused.isInLayout &&
                    insets?.isVisible(
                        androidx.core.view.WindowInsetsCompat.Type
                            .ime(),
                    ) == true &&
                    insets
                        .getInsets(
                            androidx.core.view.WindowInsetsCompat.Type
                                .ime(),
                        ).bottom > 0
            }
            ready
        }
    }

    private fun enterTime(
        hour: Int,
        minute: Int,
    ) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("alarm-hour").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("time-input-mode").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag("alarm-hour").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("time-input-mode").performClick()
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("alarm-hour").fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithTag("alarm-minute").fetchSemanticsNodes().size == 1
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val use24Hour =
            android.text.format.DateFormat
                .is24HourFormat(context)
        val displayHour = if (use24Hour) hour else (hour % 12).takeIf { it != 0 } ?: 12
        val hourInput = compose.onNodeWithTag("alarm-hour")
        focusPickerInput(hourInput)
        hourInput.performTextReplacement(displayHour.toString())
        val minuteInput = compose.onNodeWithTag("alarm-minute")
        focusPickerInput(minuteInput)
        minuteInput.performTextReplacement(minute.toString().padStart(2, '0'))
        if (!use24Hour) {
            val label = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("a", context.resources.configuration.locales[0]))
            compose.onNode(hasText(label) and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
        }
    }

    private fun confirmTime(
        hour: Int,
        minute: Int,
    ) {
        enterTime(hour, minute)
        compose.onNodeWithText("Done").performClick()
    }

    @Test fun timeDoneChangesOnlyTheDraftUntilSave() {
        var saved: SunriseSchedule? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { EditorFixture { SunriseScheduleEditor(alarm, "condition-1", true, false, null, { saved = it }, {}) } }
        timeControl(schedule.wakeTime).performScrollTo().performClick()
        confirmTime(8, 15)
        compose.runOnIdle { assertEquals(null, saved) }
        saveEditor()
        compose.runOnIdle { assertEquals(schedule.copy(wakeTime = LocalTime.of(8, 15)), saved) }
    }

    @Test fun cancellingTimeDiscardsThePickerChange() {
        var saved: SunriseSchedule? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { EditorFixture { SunriseScheduleEditor(alarm, "condition-1", true, false, null, { saved = it }, {}) } }
        timeControl(schedule.wakeTime).performScrollTo().performClick()
        enterTime(8, 15)
        compose.onNodeWithText("Cancel").performClick()
        saveEditor()
        compose.runOnIdle { assertEquals(schedule, saved) }
    }

    @Test fun theMaterialDialAcceptsAnHourTap() {
        var chosen: LocalTime? = null
        compose.setContent { EditorFixture { AlarmTimePicker(LocalTime.of(7, 0), {}, { chosen = it }) } }
        org.junit.Assume.assumeTrue(
            "A dial requires the ordinary-size device profile",
            compose.onAllNodesWithTag("time-input-mode").fetchSemanticsNodes().isNotEmpty(),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val description =
            context.getString(
                if (android.text.format.DateFormat
                        .is24HourFormat(context)
                ) {
                    androidx.compose.material3.R.string.m3c_time_picker_hour_24h_suffix
                } else {
                    androidx.compose.material3.R.string.m3c_time_picker_hour_suffix
                },
                8,
            )
        compose.onNodeWithContentDescription(description).performTouchInput { click() }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(LocalTime.of(8, 0), chosen) }
    }

    @Test fun choosingNoonRetainsTheCorrectPeriod() {
        var chosen: LocalTime? = null
        compose.setContent { EditorFixture { AlarmTimePicker(LocalTime.MIDNIGHT, {}, { chosen = it }) } }
        confirmTime(12, 0)
        compose.runOnIdle { assertEquals(LocalTime.NOON, chosen) }
    }

    @Test fun backCancelsTheTimePickerWithoutAcceptingIt() {
        var cancelled = false
        compose.setContent {
            EditorFixture {
                AlarmTimePicker(
                    LocalTime.of(7, 0),
                    { cancelled = true },
                    { error("Back accepted the picker") },
                )
            }
        }
        compose.waitForIdle()
        Thread.sleep(500)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(2_000) { cancelled }
    }

    @Test fun tappingTheBackdropCancelsWithoutAcceptingThePicker() {
        var cancelled = false
        compose.setContent {
            EditorFixture { AlarmTimePicker(LocalTime.of(7, 0), { cancelled = true }, { error("The backdrop accepted the picker") }) }
        }
        compose.waitForIdle()
        compose.onNode(isDialog()).performTouchInput {
            click(
                androidx.compose.ui.geometry
                    .Offset(4f, height / 2f),
            )
        }
        compose.waitUntil(2_000) { cancelled }
    }

    @Test fun invalidTimeCannotApplyThePreviousValidValue() {
        compose.setContent { EditorFixture { AlarmTimePicker(LocalTime.of(7, 0), {}, { error("Invalid time was accepted") }) } }
        if (compose
                .onAllNodesWithTag(
                    "time-input-mode",
                ).fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            compose.onNodeWithTag("time-input-mode").performClick()
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("99")
        compose.onNodeWithText("Done").assertIsNotEnabled()
        if (compose.onAllNodesWithTag("time-input-mode").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("time-input-mode").assertIsNotEnabled().performTouchInput { click() }
            compose.onNodeWithText("Done").assertIsNotEnabled()
        }
        compose.onNodeWithText("Cancel").assertIsEnabled()
    }

    @Test fun invalidDateCannotApplyThePreviousValidValue() {
        compose.setContent { EditorFixture { AlarmDatePicker(LocalDate.of(2026, 10, 20), {}, { error("Invalid date was accepted") }) } }
        openDateInput()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("13322026")
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsEnabled()
    }

    @Test fun cancellingDateDiscardsThePickerChange() {
        var saved: SunriseSchedule? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { EditorFixture { SunriseScheduleEditor(alarm, "condition-1", true, false, null, { saved = it }, {}) } }
        compose
            .onNodeWithText(
                schedule.onceDate!!.format(
                    DateTimeFormatter
                        .ofLocalizedDate(FormatStyle.FULL)
                        .withLocale(context.resources.configuration.locales[0]),
                ),
            ).performScrollTo()
            .performClick()
        enterDate(LocalDate.of(2026, 11, 2))
        compose.onNodeWithText("Cancel").performClick()
        saveEditor()
        compose.runOnIdle { assertEquals(schedule, saved) }
    }

    @Test
    fun weeklySelectionDoesNotRetainTheOneNightDate() {
        var saved: SunriseSchedule? = null
        compose.setContent {
            EditorFixture {
                SunriseScheduleEditor(alarm, "condition-1", true, false, null, { saved = it }, {})
            }
        }

        compose.onNodeWithText("Repeat").performClick()
        compose.onNodeWithText("Save alarm").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(null, saved?.onceDate)
            assertEquals(DayOfWeek.entries.toSet(), saved?.weekdays)
        }
    }
}
