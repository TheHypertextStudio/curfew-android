package studio.hypertext.curfew.platform

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseOccurrencePhase
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.alarm.SunriseScheduleController
import studio.hypertext.curfew.alarm.SunriseScheduleLock
import studio.hypertext.curfew.alarm.SunriseOccurrenceCompletion
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.callback.callbackPollScheduler
import studio.hypertext.curfew.checkin.CheckInCatalog
import studio.hypertext.curfew.checkin.PlaceAlarmCompletion
import studio.hypertext.curfew.checkin.PlaceCheckInAlarmLifecycle
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.persistence.SunriseAlarmPreferences

private val Context.sunriseAlarmDataStore by preferencesDataStore(name = "sunrise_alarm")

internal class SunriseScheduler(
    context: Context,
    injectedCallbackPolling: CallbackPollScheduler? = null,
) : PlaceCheckInAlarmLifecycle {
    private val context = context.applicationContext
    private val callbackPolling by lazy { injectedCallbackPolling ?: this.context.callbackPollScheduler() }
    private val preferences = SunriseAlarmPreferences(this.context.sunriseAlarmDataStore)
    private val repository = AlarmRuntimeRepository(this.context)
    val state = preferences.state

    private val runtime by lazy {
        val campaigns = object : SunriseCampaignStore {
            override suspend fun load(campaignId: String) = repository.load(campaignId)

            override suspend fun callbackId(campaignId: String) = repository.callbackId(campaignId)

            override suspend fun putScheduled(state: AlarmCampaignState.Scheduled, callbackId: String) {
                repository.save(state, callbackId = callbackId)
            }

            override suspend fun removeScheduled(campaignId: String) {
                val dao = CurfewDatabase.open(this@SunriseScheduler.context).alarmCampaignDao()
                dao.deleteScheduled(campaignId)
                callbackPolling.cancel(campaignId)
            }
        }
        SunriseCampaignRuntime(
            campaigns,
            AndroidAlarmClockGateway(this.context),
            NativeDeviceProofAuthenticator(this.context).deviceId(),
        ) { callbackId ->
            val prefs = CurfewPreferences(this.context).state.first()
            val catalog = CheckInCatalog(this.context)
            val method = catalog.find(callbackId)
            AndroidAlarmReadiness(this.context).evaluate(prefs).canArm &&
                prefs.isCallbackReady(callbackId) && method?.available == true &&
                (!method.placeCode || catalog.placeCameraReady())
        }
    }

    private val controller by lazy { SunriseScheduleController(preferences, runtime, SunriseScheduleLock.mutex) }

    suspend fun save(
        schedule: SunriseSchedule,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SavedSunriseAlarm = withContext(Dispatchers.IO) { controller.save(schedule, now, zone) }

    override suspend fun revokeCondition(id: String, revoke: suspend () -> Unit) = withContext(Dispatchers.IO) {
        controller.revokeCondition(id, Instant.now(), ZoneId.systemDefault(), revoke)
    }

    suspend fun restore(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SavedSunriseAlarm? = withContext(Dispatchers.IO) { controller.restore(now, zone) }

    suspend fun consume(campaignId: String): SavedSunriseAlarm? =
        withContext(Dispatchers.IO) { controller.consume(campaignId) }

    suspend fun completeOccurrence(campaignId: String) = withContext(Dispatchers.IO) {
        SunriseOccurrenceCompletion(
            consume = { consume(it) },
            scheduleNext = { restore() },
            cancelTransition = { AndroidAlarmClockGateway(context).cancel(it) },
            cancelPolling = callbackPolling::cancel,
        ).complete(campaignId)
    }

    /** Finish an alarm already released by a place code and enqueue follow-up if needed. */
    override suspend fun finishOccurrence(campaignId: String): PlaceAlarmCompletion =
        try {
            completeOccurrence(campaignId)
            PlaceAlarmCompletion.COMPLETE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            try {
                callbackPolling.schedule(campaignId)
            } catch (_: Exception) {
                Log.w(LOG_TAG, RETRY_UNAVAILABLE_MESSAGE)
            }
            PlaceAlarmCompletion.NEXT_ALARM_UNAVAILABLE
        }

    suspend fun currentPhase(): SunriseOccurrencePhase? = withContext(Dispatchers.IO) {
        preferences.read()?.occurrence?.let { runtime.phase(it.campaignId) }
    }

    companion object {
        private const val LOG_TAG = "SunriseScheduler"
        private const val RETRY_UNAVAILABLE_MESSAGE = "Could not request alarm completion retry"

        suspend fun <T> serialized(action: suspend () -> T): T = SunriseScheduleLock.serialized(action)
    }
}
