package studio.hypertext.curfew.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.hypertext.curfew.checkin.CheckInCatalog
import studio.hypertext.curfew.di.IoDispatcher
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.platform.AndroidAlarmReadiness
import studio.hypertext.curfew.platform.AndroidAlarmSoundPreview
import studio.hypertext.curfew.platform.SunriseScheduler
import studio.hypertext.curfew.readiness.AlarmPresentation
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.AlarmSoundCheck
import studio.hypertext.curfew.readiness.AlarmSoundCheckState
import studio.hypertext.curfew.ui.alarmSaveError
import javax.inject.Inject

data class DailyAlarmUiState(
    val sunriseAlarm: SavedSunriseAlarm? = null,
    val readiness: AlarmReadiness = AlarmReadiness(
        canArm = false,
        requiredActions = emptySet(),
        disclosures = emptySet(),
        presentation = AlarmPresentation.FULL_SCREEN,
    ),
    val soundCheckState: AlarmSoundCheckState = AlarmSoundCheckState.CLOSED,
    val alarmBusy: Boolean = false,
    val alarmError: String? = null,
    val conditionLabel: String? = null,
    val conditionVerified: Boolean = false,
    val canArm: Boolean = false,
)

/**
 * Screen-level ViewModel managing Daily Alarm overview, scheduling, and sound readiness.
 */
@HiltViewModel
class DailyAlarmViewModel @Inject constructor(
    private val sunriseScheduler: SunriseScheduler,
    private val preferences: CurfewPreferences,
    private val checkInCatalog: CheckInCatalog,
    private val alarmReadiness: AndroidAlarmReadiness,
    private val soundPreview: AndroidAlarmSoundPreview,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val soundCheck by lazy {
        AlarmSoundCheck(soundPreview::play, soundPreview::stop) {
            viewModelScope.launch(ioDispatcher) { preferences.setTestSoundConfirmed(true) }
        }
    }
    private var testSoundJob: Job? = null

    private val _alarmBusy = MutableStateFlow(false)
    private val _alarmError = MutableStateFlow<String?>(null)

    init {
        addCloseable {
            cancelTestSound()
            soundCheck.close()
        }
    }

    val uiState: StateFlow<DailyAlarmUiState> = combine(
        sunriseScheduler.state.catch { emit(null) },
        preferences.state,
        soundCheck.state,
        _alarmBusy,
        _alarmError,
    ) { savedAlarm, prefState, soundState, busy, error ->
        val selectedCheckIn = prefState.selectedCallbackId?.let { checkInCatalog.find(it) }
        val alarmCheckIn = savedAlarm?.let { checkInCatalog.find(it.schedule.callbackId) }
        val readiness = alarmReadiness.evaluate(prefState)
        val conditionVerified = selectedCheckIn?.available == true &&
            prefState.selectedCallbackId == prefState.verifiedCallbackId
        val canArm = readiness.canArm && conditionVerified

        DailyAlarmUiState(
            sunriseAlarm = savedAlarm,
            readiness = readiness,
            soundCheckState = soundState,
            alarmBusy = busy,
            alarmError = error,
            conditionLabel = alarmCheckIn?.displayLabel ?: selectedCheckIn?.displayLabel,
            conditionVerified = conditionVerified && savedAlarm?.schedule?.callbackId == prefState.selectedCallbackId,
            canArm = canArm && (savedAlarm == null || savedAlarm.schedule.callbackId == prefState.selectedCallbackId),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DailyAlarmUiState(),
    )

    fun saveAlarm(schedule: SunriseSchedule) {
        if (_alarmBusy.value) return
        _alarmBusy.value = true
        _alarmError.value = null
        viewModelScope.launch(ioDispatcher) {
            try {
                sunriseScheduler.save(schedule)
            } catch (failure: Exception) {
                _alarmError.value = alarmSaveError(failure)
            } finally {
                _alarmBusy.value = false
            }
        }
    }

    fun toggleAlarm(enabled: Boolean) {
        if (_alarmBusy.value) return
        _alarmBusy.value = true
        _alarmError.value = null
        viewModelScope.launch(ioDispatcher) {
            try {
                val current = sunriseScheduler.state.first()?.schedule ?: return@launch
                sunriseScheduler.save(current.copy(enabled = enabled))
            } catch (failure: Exception) {
                _alarmError.value = alarmSaveError(failure)
            } finally {
                _alarmBusy.value = false
            }
        }
    }

    fun playTestSound() {
        testSoundJob?.cancel()
        testSoundJob = viewModelScope.launch(ioDispatcher) { soundCheck.play() }
    }

    fun cancelTestSound() {
        testSoundJob?.cancel()
        testSoundJob = null
        soundCheck.close()
    }

    fun confirmSound() {
        viewModelScope.launch(ioDispatcher) { soundCheck.confirm() }
    }

    fun acknowledgeLimitations() {
        viewModelScope.launch(ioDispatcher) { preferences.acknowledgeLimitations() }
    }
}
