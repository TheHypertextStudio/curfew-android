package studio.hypertext.curfew.platform

import android.app.NotificationManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class FreshAlarmChannelsInstrumentedTest {
    @Test
    fun startupAndReadinessReadsDoNotCreateTheAlarmChannel() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("freshAlarmChannels") == "true")
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = PerpetualAlarmService.ALARM_CHANNEL_ID
        assertNull("Run on a fresh disposable installation", manager.getNotificationChannel(channelId))
        assertFalse(PerpetualAlarmService.alarmChannelAllowsPresentation(context))
        assertNull(manager.getNotificationChannel(channelId))
        PerpetualAlarmService.ensureChannels(context)
        assertNotNull(manager.getNotificationChannel(channelId))
    }
}
