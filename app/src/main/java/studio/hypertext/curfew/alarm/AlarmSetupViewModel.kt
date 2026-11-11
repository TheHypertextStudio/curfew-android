package studio.hypertext.curfew.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.hypertext.curfew.checkin.CheckInCatalog
import studio.hypertext.curfew.persistence.CurfewPreferenceState
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.persistence.FirstUseChoice
import studio.hypertext.curfew.persistence.FirstUseLoadState
import studio.hypertext.curfew.persistence.FirstUsePreferences
import studio.hypertext.curfew.platform.SunriseScheduler
import studio.hypertext.curfew.readiness.AlarmReadiness

/**
 * Reads the alarm's current platform capabilities and evaluates them alongside saved readiness
 * preferences. This port does not request permissions or create notification channels.
 */
internal fun interface AlarmReadinessReader {
    fun evaluate(preferences: CurfewPreferenceState): AlarmReadiness
}

/**
 * Shares the saved alarm and prerequisites across the alarm setup journey. It persists changes
 * and exposes an entry snapshot for cold launch; it does not own route forms, navigation, or
 * playback.
 */
@HiltViewModel
internal class AlarmSetupViewModel @Inject constructor(
    private val scheduler: SunriseScheduler,
    private val preferences: CurfewPreferences,
    private val firstUsePreferences: FirstUsePreferences,
    private val checkIns: CheckInCatalog,
    private val readiness: AlarmReadinessReader,
) : ViewModel() {
    private data class SaveState(val busy: Boolean = false, val error: AlarmSaveFailure? = null)

    private val save = MutableStateFlow(SaveState())
    private val capabilityRevision = MutableStateFlow(0)
    private val firstUseRevision = MutableStateFlow(0)
    private val savedAlarm = scheduler.state.catch { emit(null) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val entry = combine(
        savedAlarm,
        firstUseRevision.flatMapLatest { firstUsePreferences.loadState },
    ) { alarm, load ->
        val firstUse = (load as? FirstUseLoadState.Ready)?.state
        AlarmSetupEntry(
            loaded = load !is FirstUseLoadState.Loading,
            loadError = load is FirstUseLoadState.Error && alarm == null,
            showWelcome = firstUse?.needsWelcome(alarm != null) == true,
            startSetup = alarm == null && firstUse?.choice == FirstUseChoice.STARTED,
            draft = firstUse?.draft.takeIf { alarm == null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmSetupEntry())

    private val setupInputs = combine(savedAlarm, preferences.state, capabilityRevision) { alarm, prefs, _ ->
        val selected = prefs.selectedCallbackId?.let { checkIns.find(it) }
        val alarmCheckIn = alarm?.schedule?.callbackId?.let { id ->
            if (id == selected?.callbackId) selected else checkIns.find(id)
        }
        AlarmSetupUiState(
            loaded = true,
            readiness = readiness.evaluate(prefs),
            sunriseAlarm = alarm,
            callbackId = prefs.selectedCallbackId,
            conditionLabel = selected?.displayLabel,
            alarmConditionLabel = alarmCheckIn?.displayLabel,
            conditionVerified = selected?.available == true && prefs.isCallbackReady(selected.callbackId) &&
                (!selected.placeCode || checkIns.placeCameraReady()),
            placeCodeSelected = selected?.placeCode == true,
        )
    }

    val state = combine(setupInputs, save) { inputs, result ->
        inputs.copy(alarmBusy = result.busy, alarmError = result.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmSetupUiState())

    fun refreshCapabilities() {
        capabilityRevision.update { it + 1 }
    }

    fun retryFirstUse() {
        firstUseRevision.update { it + 1 }
    }

    fun onResume() {
        refreshCapabilities()
        viewModelScope.launch {
            try {
                scheduler.restore()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Readiness reports any required repairs.
            }
        }
    }

    fun saveAlarm(schedule: SunriseSchedule) = runSave { scheduler.save(schedule) }

    fun toggleAlarm(enabled: Boolean) = runSave {
        val schedule = scheduler.state.first()?.schedule ?: return@runSave
        scheduler.save(schedule.copy(enabled = enabled))
    }

    private fun runSave(operation: suspend () -> Unit) {
        if (save.value.busy) return
        save.value = SaveState(busy = true)
        viewModelScope.launch {
            try {
                operation()
                save.value = SaveState()
            } catch (cancelled: CancellationException) {
                save.value = SaveState()
                throw cancelled
            } catch (failure: Exception) {
                save.value = SaveState(error = alarmSaveFailure(failure))
            }
        }
    }

    suspend fun chooseWelcome(start: Boolean) {
        if (start) firstUsePreferences.chooseContinue() else firstUsePreferences.chooseNotNow()
    }

    suspend fun prepareDraft(draft: AlarmSetupDraft) {
        firstUsePreferences.saveDraft(draft)
    }

    fun clearDraft() {
        viewModelScope.launch {
            try {
                firstUsePreferences.clearDraft()
            } catch (_: java.io.IOException) {
                // The saved alarm takes precedence over a stale draft on next launch.
            }
        }
    }

    fun acknowledgeLimitations() {
        viewModelScope.launch { preferences.acknowledgeLimitations() }
    }
}
