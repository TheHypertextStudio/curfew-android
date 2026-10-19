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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway

/** The window flag proves our presentation request, not that an OEM unlocks the keyguard. */
class AlarmKeyguardPresentationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val id = UUID.randomUUID().toString()

    @After
    fun removeFixture() = runBlocking { CurfewDatabase.open(context).alarmCampaignDao().delete(id) }

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
