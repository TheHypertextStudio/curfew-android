package studio.hypertext.curfew.ui

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.platform.cleanupAlarmFixture
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway

/** The window flag proves our presentation request, not that an OEM unlocks the keyguard. */
class AlarmKeyguardPresentationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val id = UUID.randomUUID().toString()

    @Before
    fun requireEmptyAlarmQueue() = runBlocking {
        check(AlarmRuntimeRepository(context).pending().isEmpty()) { "The keyguard fixture must not interrupt another alarm" }
    }

    @After
    fun removeFixture() = runBlocking { cleanupAlarmFixture(context, id) }

    private fun keepScreenOnFlag(showAlarm: Boolean, campaignId: String? = null, expectedOn: Boolean = false): Int {
        var flags = 0
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_SHOW_ALARM, showAlarm)
            campaignId?.let { putExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID, it) }
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Disk recovery and the Compose subscription complete after ActivityScenario reaches RESUMED.
            repeat(30) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    flags = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                }
                if (expectedOn && flags != 0) return@repeat
                Thread.sleep(50)
            }
        }
        return flags
    }

    @Test
    fun fixtureCleanupCancelsItsRegisteredAlarmClock() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow",
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        val alarms = context.getSystemService(android.app.AlarmManager::class.java)
        org.junit.Assert.assertNull("This fixture needs an empty alarm-clock queue", alarms.nextAlarmClock)
        val at = Instant.ofEpochMilli(Instant.now().plusSeconds(3600).toEpochMilli())
        AlarmRuntimeRepository(context).save(AlarmCampaignState.Scheduled(id, at, AlarmRecurrencePolicy.default(), setOf("fixture-device")))
        AndroidAlarmClockGateway(context).setAlarmClock(id, at)
        assertEquals(at.toEpochMilli(), alarms.nextAlarmClock?.triggerTime)
        removeFixture()
        val deadline = android.os.SystemClock.elapsedRealtime() + 2_000
        while (alarms.nextAlarmClock != null && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(25)
        }
        org.junit.Assert.assertNull("Fixture cleanup must cancel its registered alarm", alarms.nextAlarmClock)
    }

    @Test
    fun alarmLaunchKeepsTheScreenOnForASavedActiveOccurrence() = runBlocking {
        val now = Instant.now()
        val state = AlarmCampaignEngine().advanceTo(
            AlarmCampaignState.Scheduled(id, now, AlarmRecurrencePolicy.default(), setOf("fixture-device")), now,
        )
        AlarmRuntimeRepository(context).save(state)
        assertNotEquals(0, keepScreenOnFlag(true, id, expectedOn = true))
    }

    @Test
    fun launcherStartDoesNotRequestKeyguardPresentation() {
        assertEquals(0, keepScreenOnFlag(false))
    }

    @Test
    fun aMissingSavedOccurrenceCannotRequestKeyguardPresentation() {
        assertEquals(0, keepScreenOnFlag(true, id))
    }
}
