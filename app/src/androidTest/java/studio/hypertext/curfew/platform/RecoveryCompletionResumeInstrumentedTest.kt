package studio.hypertext.curfew.platform

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.OwnerRecoveryAuthorization
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.callback.AndroidWakeConditionSetup
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.callback.LocalHttpsVerifier
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.persistence.CurfewPreferences

class RecoveryCompletionResumeInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val runtime = AlarmRuntimeRepository(context)
    private val scheduler = SunriseScheduler(context)

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    @Test
    fun terminalAlarmTransitionFinishesInterruptedRecoveryAndSchedulesTheNextMorning() = runBlocking {
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        CurfewPreferences(context).apply { acknowledgeLimitations(); recordTestSound() }
        LocalHttpsVerifier().use { verifier ->
            val setup = AndroidWakeConditionSetup(context)
            val definition = setup.save("Recovery continuation fixture", verifier.endpoint, null)
            verifier.definition = definition
            assertTrue(setup.verify(definition.callbackId))
            val start = Instant.now().plusSeconds(3).atZone(ZoneOffset.UTC)
            val saved = scheduler.save(SunriseSchedule(
                UUID.randomUUID().toString(), start.toLocalTime(), null,
                DayOfWeek.entries.toSet(), true, definition.callbackId,
            ), zone = ZoneOffset.UTC)
            val id = requireNotNull(saved.occurrence).campaignId
            var nextId: String? = null
            try {
                val state = requireNotNull(runtime.load(id))
                runtime.save(AlarmCampaignEngine().advanceTo(state, requireNotNull(saved.occurrence).startsAt))
                var elapsed = 0L
                val authorization = OwnerRecoveryAuthorization { elapsed }
                authorization.beginHold(id, "The controlled check-in is unavailable and I need to leave for work.")
                elapsed = 3_000
                val request = requireNotNull(authorization.requestConfirmation())
                val approval = requireNotNull(authorization.finishConfirmation(request.requestId, true))
                assertTrue(AlarmOwnerRecoveryRepository(context).recover(approval))
                // Only the atomic outcome survived. No completion callback or schedule cleanup ran.
                context.sendBroadcast(Intent(context, AlarmTransitionReceiver::class.java)
                    .setAction(AndroidAlarmClockGateway.ACTION_ALARM_TRANSITION)
                    .putExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID, id))
                val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
                do {
                    val restored = scheduler.state.first()
                    nextId = restored?.occurrence?.campaignId?.takeUnless { it == id }
                    if (nextId != null) break
                    Thread.sleep(100)
                } while (android.os.SystemClock.elapsedRealtime() < deadline)
                assertTrue("A committed recovery must not leave the saved alarm pointing at its terminal occurrence", nextId != null)
                assertTrue(runtime.load(requireNotNull(nextId)) is AlarmCampaignState.Scheduled)
                assertTrue(runtime.load(id) is AlarmCampaignState.Overridden)
                assertTrue(CurfewDatabase.open(context).alarmRecoveryAuditDao().forCampaign(id).size == 1)
            } finally {
                scheduler.state.first()?.let { scheduler.save(it.schedule.copy(enabled = false)) }
                for (fixtureId in listOfNotNull(id, nextId)) {
                    AndroidAlarmClockGateway(context).cancel(fixtureId)
                    CallbackPollScheduler.cancel(context, fixtureId)
                    CurfewDatabase.open(context).alarmCampaignDao().delete(fixtureId)
                    CurfewDatabase.open(context).alarmRecoveryAuditDao().deleteForCampaign(fixtureId)
                }
                CallbackConfigurationRepository(context).delete(definition.callbackId)
            }
        }
    }
}
