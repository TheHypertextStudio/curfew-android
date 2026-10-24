package studio.hypertext.curfew.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AlarmNotificationChannelInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun notificationPresentationDoesNotStartAnotherAlarmSound() {
        PerpetualAlarmService.ensureChannels(context)
        val channel =
            context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(PerpetualAlarmService.ALARM_CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNull("The service player must be the only alarm audio owner", channel.sound)
        assertTrue(PerpetualAlarmService.alarmChannelAllowsPresentation(context))
    }

    @Test
    fun migrationPreservesBlockedNotificationsAndVibrationChoice() {
        val previous =
            NotificationChannel("previous", "Alarms", NotificationManager.IMPORTANCE_NONE).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
            }
        val channel = PerpetualAlarmService.alarmNotificationChannel(context, previous)
        assertEquals("Migration must not re-enable blocked notifications", previous.importance, channel.importance)
        assertFalse(channel.shouldVibrate())
        assertEquals(previous.lockscreenVisibility, channel.lockscreenVisibility)
        assertFalse(channel.canShowBadge())
        assertNull(channel.sound)
    }

    @Test
    fun migrationPreservesReducedImportanceAndCustomVibration() {
        val previous =
            NotificationChannel("previous", "Alarms", NotificationManager.IMPORTANCE_LOW).apply {
                vibrationPattern = longArrayOf(0, 100, 200, 300)
                enableVibration(true)
                if (Build.VERSION.SDK_INT >= 35) {
                    setVibrationEffect(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                enableLights(true)
                lightColor = 0xff123456.toInt()
            }
        val channel = PerpetualAlarmService.alarmNotificationChannel(context, previous)
        assertEquals(previous.importance, channel.importance)
        assertArrayEquals(previous.vibrationPattern, channel.vibrationPattern)
        assertTrue(channel.shouldVibrate())
        if (Build.VERSION.SDK_INT >= 35) assertEquals(previous.vibrationEffect, channel.vibrationEffect)
        assertTrue(channel.shouldShowLights())
        assertEquals(previous.lightColor, channel.lightColor)
        assertNull(channel.sound)
    }

    @Test
    fun userSelectedNotificationSoundRequiresRepair() {
        val channel = PerpetualAlarmService.alarmNotificationChannel(context, null)
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
        assertFalse(
            "A notification sound would retain a second audio focus owner",
            PerpetualAlarmService.alarmChannelAllowsPresentation(channel),
        )
    }

    @Test
    fun persistedMigrationPreservesBlockedChannelAndDoesNotOverwriteLaterChoices() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val legacyId = "focus-migration-${UUID.randomUUID()}"
        val newId = "$legacyId-playback"
        try {
            manager.createNotificationChannel(
                NotificationChannel(legacyId, "Fixture", NotificationManager.IMPORTANCE_NONE).apply {
                    enableVibration(false)
                },
            )
            val previous = manager.getNotificationChannel(legacyId)
            PerpetualAlarmService.ensureAlarmNotificationChannel(
                manager,
                PerpetualAlarmService.alarmNotificationChannel(context, previous, newId),
            )
            val migrated = manager.getNotificationChannel(newId)
            assertEquals(NotificationManager.IMPORTANCE_NONE, migrated.importance)
            assertFalse(migrated.shouldVibrate())
            assertNull(migrated.sound)
            // A later setup run must not replace the persisted channel with fresh defaults.
            PerpetualAlarmService.ensureAlarmNotificationChannel(
                manager,
                PerpetualAlarmService.alarmNotificationChannel(context, null, newId),
            )
            assertEquals(migrated, manager.getNotificationChannel(newId))
        } finally {
            manager.deleteNotificationChannel(newId)
            manager.deleteNotificationChannel(legacyId)
        }
    }

    @Test
    fun anExistingPlaybackChannelKeepsUserSelectedSoundAndRequiresRepair() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = "focus-owner-${UUID.randomUUID()}"
        try {
            val selected =
                PerpetualAlarmService.alarmNotificationChannel(context, null, id).apply {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
                    enableVibration(false)
                }
            manager.createNotificationChannel(selected)
            val persisted = manager.getNotificationChannel(id)
            PerpetualAlarmService.ensureAlarmNotificationChannel(
                manager,
                PerpetualAlarmService.alarmNotificationChannel(context, null, id),
            )
            val afterSetup = manager.getNotificationChannel(id)
            assertEquals(persisted, afterSetup)
            assertFalse(
                "Keep the owner's choice and request repair instead of silently changing it",
                PerpetualAlarmService.alarmChannelAllowsPresentation(afterSetup),
            )
        } finally {
            manager.deleteNotificationChannel(id)
        }
    }
}
