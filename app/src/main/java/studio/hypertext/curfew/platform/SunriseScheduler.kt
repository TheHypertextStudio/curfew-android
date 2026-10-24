package studio.hypertext.curfew.platform

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseOccurrencePhase
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.alarm.SunriseScheduleController
import studio.hypertext.curfew.alarm.SunriseOccurrenceCompletion
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.persistence.SunriseAlarmPreferences

private val Context.sunriseAlarmDataStore by preferencesDataStore(name = "sunrise_alarm")

class SunriseScheduler(context: Context) {
    private val context = context.applicationContext
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
                CallbackPollScheduler.cancel(this@SunriseScheduler.context, campaignId)
            }
        }
        SunriseCampaignRuntime(
            campaigns,
            AndroidAlarmClockGateway(this.context),
            NativeDeviceProofAuthenticator(this.context).deviceId(),
        ) { callbackId ->
            val prefs = CurfewPreferences(this.context).state.first()
            val method = studio.hypertext.curfew.checkin.CheckInCatalog(this.context).find(callbackId)
            AndroidAlarmReadiness(this.context).evaluate(prefs).canArm &&
                prefs.isCallbackReady(callbackId) && method?.available == true &&
                (!method.placeCode || studio.hypertext.curfew.checkin.PlaceCheckInRepository(this.context).cameraReady())
        }
    }

    private val controller by lazy { SunriseScheduleController(preferences, runtime, executionMutex) }

    suspend fun save(
        schedule: SunriseSchedule,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SavedSunriseAlarm = withContext(Dispatchers.IO) { controller.save(schedule, now, zone) }

    suspend fun revokeCondition(callbackId: String, revoke: suspend () -> Unit) = withContext(Dispatchers.IO) {
        controller.revokeCondition(callbackId, Instant.now(), ZoneId.systemDefault(), revoke)
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
            cancelPolling = { CallbackPollScheduler.cancel(context, it) },
        ).complete(campaignId)
    }

    suspend fun currentPhase(): SunriseOccurrencePhase? = withContext(Dispatchers.IO) {
        preferences.read()?.occurrence?.let { runtime.phase(it.campaignId) }
    }

    companion object {
        private val executionMutex = Mutex()

        suspend fun <T> serialized(action: suspend () -> T): T = executionMutex.withLock { action() }
    }
}
