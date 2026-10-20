package studio.hypertext.curfew.platform

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.persistence.CurfewDatabase

/** Requires an explicitly provisioned disposable emulator. Never supplies a real owner's credential. */
class OwnerRecoveryJourneyInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val automation get() = instrumentation.uiAutomation
    private val runtime = AlarmRuntimeRepository(context)
    private val database = CurfewDatabase.open(context)
    private val id = "credential-fixture-${UUID.randomUUID()}"
    private val reason = "My test check-in is unavailable and I need to leave for work this morning."
    private var started = false

    private fun shell(command: String) {
        automation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
    }

    private fun fixturePin(): String {
        val pin = InstrumentationRegistry.getArguments().getString("ownerRecoveryFixturePin")
        assumeTrue("Use the disposable-emulator credential acceptance script", pin != null)
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Credential fixture requires an emulator" }
        check(requireNotNull(pin).matches(Regex("[0-9]{6}")))
        check(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        return pin
    }

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
        do {
            if (predicate()) return
            Thread.sleep(100)
        } while (android.os.SystemClock.elapsedRealtime() < deadline)
        capture("failed")
        error("Timed out: $description")
    }

    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (predicate(node)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it)?.let { found -> return found } }
            return null
        }
        return automation.rootInActiveWindow?.let(::visit)
    }

    private fun click(text: String) {
        fun target(): AccessibilityNodeInfo? {
            var node = find { it.text?.toString() == text || it.contentDescription?.toString() == text } ?: return null
            while (!node.isClickable) node = node.parent ?: return null
            return node.takeIf { it.isEnabled }
        }
        await("$text is available") { target() != null }
        if (requireNotNull(target()).performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
        capture("failed")
        error("Could not activate $text")
    }

    private fun audioActive() = context.getSystemService(AudioManager::class.java)
        .activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }

    private fun capture(name: String) {
        val output = context.cacheDir.resolve("owner-recovery-journey").apply { mkdirs() }
        automation.takeScreenshot()?.let { bitmap ->
            output.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun describe(node: AccessibilityNodeInfo, depth: Int = 0): String = buildString {
            append(" ".repeat(depth)).append(node.packageName).append(' ').append(node.className)
                .append(' ').append(node.text).append(' ').append(node.contentDescription).append('\n')
            for (index in 0 until node.childCount) node.getChild(index)?.let { append(describe(it, depth + 1)) }
        }
        automation.rootInActiveWindow?.let { output.resolve("$name.txt").writeText(describe(it)) }
    }

    private fun openRecovery(): ActivityScenario<MainActivity> = runBlocking {
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        val now = Instant.now()
        runtime.save(AlarmCampaignEngine().advanceTo(
            AlarmCampaignState.Scheduled(id, now, AlarmRecurrencePolicy.default(), setOf("fixture")), now,
        ))
        started = true
        ContextCompat.startForegroundService(context, PerpetualAlarmService.startIntent(context, id, 1, "Test check-in", null))
        await("alarm audio before recovery") { audioActive() }
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_SHOW_ALARM, true)
            putExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID, id)
        })
        click("Alarm recovery")
        await("reason field") { find { it.className?.toString() == "android.widget.EditText" } != null }
        check(requireNotNull(find { it.className?.toString() == "android.widget.EditText" })
            .performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reason)
            }))
        click("Hold for 3 seconds")
        await("Android credential prompt") {
            automation.rootInActiveWindow?.packageName?.toString() == "com.android.systemui" &&
                find { it.text?.toString() == "Confirm alarm recovery" } != null &&
                find { it.className?.toString() == "android.widget.EditText" } != null
        }
        capture("credential-prompt")
        scenario
    }

    @Test
    fun androidCredentialConfirmationRecordsRecoveryAndStopsAudio() = runBlocking {
        val pin = fixturePin()
        openRecovery().use {
            shell("input text $pin")
            shell("input keyevent KEYCODE_ENTER")
            await("credential-authorized override and audio stop") {
                runBlocking { runtime.load(id) is AlarmCampaignState.Overridden } && !audioActive()
            }
            assertTrue(runtime.load(id) is AlarmCampaignState.Overridden)
            assertEquals(reason, database.alarmRecoveryAuditDao().forCampaign(id).single().reason)
            capture("recovered")
        }
    }

    @Test
    fun cancelingAndroidCredentialConfirmationKeepsAudioAndDoesNotRecordRecovery() = runBlocking {
        fixturePin()
        openRecovery().use {
            shell("input keyevent KEYCODE_BACK")
            val keyboardDeadline = android.os.SystemClock.elapsedRealtime() + 2_000
            while (android.os.SystemClock.elapsedRealtime() < keyboardDeadline &&
                find { it.text?.toString() == "Confirm alarm recovery" } != null
            ) Thread.sleep(100)
            if (find { it.text?.toString() == "Confirm alarm recovery" } != null) {
                shell("input keyevent KEYCODE_BACK")
            }
            await("canceled confirmation is reported") {
                find { it.text?.toString() == "Confirmation canceled." } != null
            }
            assertTrue(audioActive())
            assertTrue(runtime.load(id) is AlarmCampaignState.Ringing)
            assertTrue(database.alarmRecoveryAuditDao().forCampaign(id).isEmpty())
            capture("canceled")
        }
    }

    @After
    fun removeFixture() = runBlocking {
        if (!started) return@runBlocking
        runtime.load(id)?.let { state ->
            if (state is AlarmCampaignState.Ringing || state is AlarmCampaignState.Quiet) {
                runtime.save(AlarmCampaignEngine().override(state, Instant.now(), "fixture-cleanup", Instant.now().plusSeconds(1)))
            }
        }
        AndroidAlarmClockGateway(context).cancel(id)
        CallbackPollScheduler.cancel(context, id)
        context.stopService(Intent(context, PerpetualAlarmService::class.java))
        database.alarmCampaignDao().delete(id)
        database.alarmRecoveryAuditDao().deleteForCampaign(id)
    }
}
