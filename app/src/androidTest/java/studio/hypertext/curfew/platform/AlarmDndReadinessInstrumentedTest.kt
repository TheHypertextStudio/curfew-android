package studio.hypertext.curfew.platform

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import studio.hypertext.curfew.persistence.CurfewPreferenceState
import studio.hypertext.curfew.readiness.ReadinessAction

class AlarmDndReadinessInstrumentedTest {
    @Test
    fun platformDndMuteIsReflectedInAlarmReadiness() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Use the guarded disposable-emulator DND driver", arguments.getString("dndAcceptance") == "true")
        val expectedFilter = requireNotNull(arguments.getString("expectedInterruptionFilter")).toInt()
        val expectedMuted = requireNotNull(arguments.getString("expectedAlarmMuted")).toBooleanStrict()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifications = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        assertEquals("The external driver must establish the requested system policy", expectedFilter, notifications.currentInterruptionFilter)
        val volume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        assertEquals("Readiness must use the effective system alarm volume", expectedMuted, volume == 0)
        val preferences = CurfewPreferenceState(null, true, true, false, null, false)
        val readiness = AndroidAlarmReadiness(context).evaluate(preferences)
        assertEquals("Muted alarms must require sound repair", expectedMuted, ReadinessAction.RAISE_ALARM_VOLUME in readiness.requiredActions)
        if (expectedMuted) assertFalse("A muted alarm must not be ready", readiness.canArm)
        println("Observed interruptionFilter=$expectedFilter alarmVolume=$volume canArm=${readiness.canArm} actions=${readiness.requiredActions}")
    }
}
