package studio.hypertext.curfew.ui

import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import studio.hypertext.curfew.MainActivity

/**
 * A campaign fires while the phone is locked and asleep, which is the only
 * situation the Perpetual Alarm exists for. MainActivity therefore asks for
 * keyguard presentation and a screen wake — but only when it was launched by the
 * alarm's full-screen intent, because it is also the launcher entry point and a
 * launcher that bypasses the keyguard is a security bug, not a feature.
 *
 * Only the gating is asserted here. Whether the keyguard actually yields is an
 * OEM behavior that no emulator reproduces faithfully, so docs/TESTING.md keeps
 * that on the physical-device checklist. FLAG_KEEP_SCREEN_ON is the observable
 * half: setShowWhenLocked and setTurnScreenOn expose no public getters, and this
 * flag is set on the same branch, so it stands in for the whole decision.
 */
class AlarmKeyguardPresentationInstrumentedTest {
    private fun intent(showAlarm: Boolean): Intent =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (showAlarm) putExtra(MainActivity.EXTRA_SHOW_ALARM, true)
        }

    private fun keepScreenOnFlag(showAlarm: Boolean): Int {
        var flags = 0
        ActivityScenario.launch<MainActivity>(intent(showAlarm)).use { scenario ->
            scenario.onActivity { activity ->
                flags = activity.window.attributes.flags and
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            }
        }
        return flags
    }

    @Test
    fun alarmLaunchKeepsTheScreenOnSoTheRingingUiSurvivesTheCampaign() {
        assertNotEquals(0, keepScreenOnFlag(showAlarm = true))
    }

    @Test
    fun launcherStartDoesNotRequestKeyguardPresentation() {
        assertEquals(0, keepScreenOnFlag(showAlarm = false))
    }
}
