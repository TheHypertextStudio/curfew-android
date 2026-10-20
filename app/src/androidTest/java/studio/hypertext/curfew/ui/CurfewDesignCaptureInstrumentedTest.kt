package studio.hypertext.curfew.ui

import android.graphics.Bitmap
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseOccurrence
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.readiness.AlarmPresentation
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.ReadinessAction
import studio.hypertext.curfew.ui.theme.CurfewTheme

/** Synthetic visual fixtures exercise the native UI; captures do not prove alarm or design acceptance. */
class CurfewDesignCaptureInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private data class Frame(val generation: Int = 0, val dark: Boolean = false, val width: Int = 390, val scale: Float = 1f)
    private var frame by mutableStateOf(Frame())
    private val imeBottom = AtomicInteger()
    private var keyboard: SoftwareKeyboardController? = null
    private val alarm = SavedSunriseAlarm(
        SunriseSchedule("design-alarm", LocalTime.of(7, 0), null, java.time.DayOfWeek.entries.take(5).toSet(), true, "design-check-in"),
        SunriseOccurrence("design-morning", LocalDate.of(2026, 10, 20), Instant.parse("2026-10-20T14:00:00Z")),
    )

    @Test fun captureDaily() = capture("daily")
    @Test fun captureEditor() = capture("editor")
    @Test fun captureReadiness() = capture("readiness")
    @Test fun captureConnectedCheckIn() = capture("check-in")
    @Test fun captureInitialServiceForm() = capture("service-form")
    @Test fun captureAccount() = capture("account")
    @Test fun captureKeyboardServiceForm() = capture("service-form-keyboard", withKeyboard = true)
    @Test fun capturePlatformTimePicker() = capturePicker(time = true)
    @Test fun capturePlatformDatePicker() = capturePicker(time = false)

    private fun capture(scene: String, withKeyboard: Boolean = false) {
        val initialForm = scene.startsWith("service-form")
        val required = if (scene == "readiness") setOf(ReadinessAction.RAISE_ALARM_VOLUME, ReadinessAction.PLAY_TEST_SOUND) else emptySet()
        val output = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("curfew-design").apply { mkdirs() }
        val manifest = JSONArray()
        compose.setContent { Fixture(initialForm, required) }
        var generation = 0
        for (dark in listOf(false, true)) for (width in listOf(320, 390)) for (scale in listOf(1f, 2f)) {
            compose.runOnIdle {
                keyboard?.hide()
                frame = Frame(++generation, dark, width, scale)
            }
            compose.waitForIdle()
            if (scene == "editor") {
                compose.onNodeWithContentDescription("Edit Sunrise").performClick()
            } else if (scene != "daily") {
                compose.onNodeWithContentDescription("Settings").performClick()
                val destination = when (scene) {
                    "readiness" -> "Alarm access and sound"
                    "account" -> "Account"
                    else -> "Check-in"
                }
                compose.onNodeWithText(destination).performScrollTo().performClick()
            }
            if (withKeyboard) {
                compose.onNodeWithText("Name").performScrollTo().performTextInput("Morning check-in")
                compose.onNodeWithText("Service address").performScrollTo().performClick()
                compose.runOnIdle { keyboard?.show() }
                compose.waitUntil(5_000) { imeBottom.get() > 0 }
            }
            compose.waitForIdle()
            val name = "$scene-${if (dark) "dark" else "light"}-$width-font${(scale * 100).toInt()}.png"
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            output.resolve(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            manifest.put(JSONObject().put("name", name).put("contentWidthDp", width).put("fontScale", scale)
                .put("dark", dark).put("bitmapWidthPx", bitmap.width).put("bitmapHeightPx", bitmap.height)
                .put("imeBottomPx", imeBottom.get()).put("syntheticFixture", true))
            compose.runOnIdle { keyboard?.hide() }
        }
        output.resolve("$scene-manifest.json").writeText(manifest.toString(2))
    }
    @Composable
    private fun Fixture(initialForm: Boolean, required: Set<ReadinessAction>, oneNight: Boolean = false) {
            val host = LocalContext.current.componentActivity()
            LaunchedEffect(host) { host.enableEdgeToEdge() }
            val current = frame
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, current.scale)) {
                val controller = LocalSoftwareKeyboardController.current
                val inset = WindowInsets.ime.getBottom(LocalDensity.current)
                SideEffect { keyboard = controller; imeBottom.set(inset) }
                key(current.generation) {
                    CurfewTheme(darkTheme = current.dark, dynamicColor = false) {
                        Box(Modifier.width(current.width.dp).fillMaxHeight()) {
                            CurfewApp(
                                readiness = AlarmReadiness(required.isEmpty(), required, emptySet(), AlarmPresentation.FULL_SCREEN),
                                accountEnrolled = false, accountSignedIn = false, accountRecoveryState = null,
                                onGrantExactAlarm = {}, onGrantNotifications = {}, onRepairAlarmChannel = {},
                                onPlayTestSound = {}, onAcknowledgeLimitations = {}, onOpenAccount = {},
                                onEnrollAccount = {}, onRestoreRecoveryKey = {}, onAcknowledgeRecoveryKey = {},
                                onSaveCallback = { _, _, _ -> }, sunriseAlarm = if (initialForm) null else if (oneNight) alarm.copy(schedule = alarm.schedule.copy(
                                    onceDate = LocalDate.of(2026, 10, 20), weekdays = emptySet(),
                                )) else alarm,
                                callbackId = if (initialForm) null else "design-check-in",
                                conditionLabel = if (initialForm) null else "Morning check-in",
                                alarmBusy = false, alarmError = null, onSaveAlarm = {}, onToggleAlarm = {},
                                conditionVerified = !initialForm,
                            )
                        }
                    }
                }
            }
    }

    private fun capturePicker(time: Boolean) {
        compose.setContent { Fixture(initialForm = false, required = emptySet(), oneNight = true) }
        compose.onNodeWithContentDescription("Edit Sunrise").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        if (time) compose.onNodeWithText(timeLabel(LocalTime.of(7, 0), context, locale)).performClick()
        else compose.onNodeWithText(LocalDate.of(2026, 10, 20).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))).performClick()
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(300, 3_000)
        val bitmap = requireNotNull(automation.takeScreenshot()) { "The platform dialog screenshot was unavailable" }
        val output = context.cacheDir.resolve("curfew-design").apply { mkdirs() }
        val name = "platform-${if (time) "time" else "date"}-picker"
        output.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        output.resolve("$name-manifest.json").writeText(JSONObject()
            .put("syntheticFixture", true).put("capture", "UiAutomation full display")
            .put("osFontScale", context.resources.configuration.fontScale)
            .put("osUiMode", context.resources.configuration.uiMode)
            .put("composeDensityDoesNotScalePlatformDialog", true)
            .put("bitmapWidthPx", bitmap.width).put("bitmapHeightPx", bitmap.height).toString(2))
    }

    private fun Context.componentActivity(): ComponentActivity = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.componentActivity()
        else -> error("The capture fixture requires a ComponentActivity host")
    }

}
