package studio.hypertext.curfew.ui

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.account.AndroidAccountRecovery
import studio.hypertext.curfew.account.OAuthTokenExchange
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRuntimeSnapshot
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.callback.AndroidWakeConditionSetup
import studio.hypertext.curfew.checkin.CheckInCatalog
import studio.hypertext.curfew.enforcement.AccessibilityServiceStatus
import studio.hypertext.curfew.enforcement.CurfewAccessibilityService
import studio.hypertext.curfew.enforcement.EnforcementPreferences
import studio.hypertext.curfew.enforcement.EnforcementPurpose
import studio.hypertext.curfew.enforcement.EnforcementSession
import studio.hypertext.curfew.enforcement.EnforcementSessionState
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.persistence.FirstUseChoice
import studio.hypertext.curfew.persistence.FirstUseLoadState
import studio.hypertext.curfew.persistence.FirstUsePreferences
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway
import studio.hypertext.curfew.platform.AndroidAlarmReadiness
import studio.hypertext.curfew.platform.AndroidAlarmSoundPreview
import studio.hypertext.curfew.readiness.AlarmSoundCheck
import studio.hypertext.curfew.readiness.AlarmSoundCheckState

/**
 * Production ViewModel managing state and domain operations according to
 * Google's Android Architecture guidelines.
 */
class CurfewViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val preferences = CurfewPreferences(application)
    private val enforcementPreferences = EnforcementPreferences(application)
    private val firstUsePreferences = FirstUsePreferences(application)
    private val sunriseScheduler = studio.hypertext.curfew.platform.SunriseScheduler(application)
    private val runtimeRepository = AlarmRuntimeRepository(application)
    private val wakeConditionSetup = AndroidWakeConditionSetup(application)
    private val accountRecovery = AndroidAccountRecovery(application)

    private val soundPreview by lazy { AndroidAlarmSoundPreview(application) }
    private val soundCheck by lazy {
        AlarmSoundCheck(soundPreview::play, soundPreview::stop) {
            viewModelScope.launch { preferences.setTestSoundConfirmed(true) }
        }
    }
    private var testSoundJob: Job? = null

    // Transient UI states
    private val _alarmBusy = MutableStateFlow(false)
    private val _alarmError = MutableStateFlow<String?>(null)
    private val _conditionBusy = MutableStateFlow(false)
    private val _conditionMessage = MutableStateFlow<String?>(null)
    private val _interceptedPackage = MutableStateFlow<String?>(null)
    private val _activeCampaignSnapshot = MutableStateFlow<AlarmRuntimeSnapshot?>(null)
    private val _accountRecoveryState = MutableStateFlow<AccountRecoveryState?>(null)
    private val _exportedVerifierId = MutableStateFlow<String?>(null)

    init {
        _accountRecoveryState.value = accountRecovery.pendingState()
    }

    private val alarmAndPrefsFlow = combine(
        preferences.state,
        sunriseScheduler.state.catch { emit(null) },
        firstUsePreferences.loadState,
    ) { pref, alarm, firstUse ->
        Triple(pref, alarm, firstUse)
    }

    private val enforcementStateFlow = combine(
        enforcementPreferences.sessionFlow,
        enforcementPreferences.chosenAppsFlow,
    ) { session, apps ->
        session to apps
    }

    private val alarmStatusFlow = combine(
        soundCheck.state,
        _alarmBusy,
        _alarmError,
    ) { soundState, busy, error ->
        Triple(soundState, busy, error)
    }

    // Consolidated reactive UI state
    val uiState: StateFlow<CurfewUiState> = combine(
        alarmAndPrefsFlow,
        enforcementStateFlow,
        alarmStatusFlow,
    ) { (prefState, savedAlarm, firstUseLoad), (session, chosenApps), (soundCheckState, alarmBusy, alarmError) ->
        val catalog = CheckInCatalog(application)
        val selectedCheckIn = prefState.selectedCallbackId?.let { catalog.find(it) }
        val alarmCheckIn = savedAlarm?.let { catalog.find(it.schedule.callbackId) }
        val readiness = AndroidAlarmReadiness(application).evaluate(prefState)
        val isAccessibilityReady = AccessibilityServiceStatus.isEnabled(application)
        val firstUseState = (firstUseLoad as? FirstUseLoadState.Ready)?.state

        val conditionVerified = selectedCheckIn?.available == true &&
            prefState.selectedCallbackId != null && prefState.selectedCallbackId == prefState.verifiedCallbackId

        CurfewUiState(
            readiness = readiness,
            sunriseAlarm = savedAlarm,
            callbackId = prefState.selectedCallbackId,
            conditionLabel = selectedCheckIn?.displayLabel,
            alarmConditionLabel = alarmCheckIn?.displayLabel,
            conditionVerified = conditionVerified,
            conditionBusy = _conditionBusy.value,
            conditionMessage = _conditionMessage.value,
            conditionSetupFileSaved = _exportedVerifierId.value != null &&
                _exportedVerifierId.value == prefState.selectedCallbackId,
            placeCodeSelected = selectedCheckIn?.placeCode == true,
            alarmBusy = alarmBusy,
            alarmError = alarmError,
            accountEnrolled = prefState.accountEnrolled,
            accountSignedIn = prefState.accountSignedIn,
            accountRecoveryState = _accountRecoveryState.value.takeIf { prefState.accountSignedIn },
            soundCheckState = soundCheckState,
            showWelcome = firstUseState?.needsWelcome(savedAlarm != null) == true,
            startSetup = savedAlarm == null && firstUseState?.choice == FirstUseChoice.STARTED,
            setupDraft = firstUseState?.draft.takeIf { savedAlarm == null },
            enforcementSession = session,
            enforcementChosenApps = chosenApps,
            isAccessibilityReady = isAccessibilityReady,
            interceptedPackage = _interceptedPackage.value,
            initialDataReady = firstUseLoad !is FirstUseLoadState.Loading,
            firstUseLoadError = firstUseLoad is FirstUseLoadState.Error && savedAlarm == null,
            ringingAlarm = _activeCampaignSnapshot.value?.state is AlarmCampaignState.Ringing ||
                _activeCampaignSnapshot.value?.state is AlarmCampaignState.Quiet,
            campaignId = savedAlarm?.occurrence?.campaignId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CurfewUiState(),
    )

    fun setAccountRecoveryState(state: AccountRecoveryState) {
        _accountRecoveryState.value = state
        if (state == AccountRecoveryState.Ready) {
            viewModelScope.launch { preferences.setAccountEnrolled(true) }
        }
    }

    fun setInterceptedPackage(packageName: String?) {
        _interceptedPackage.value = packageName
    }

    fun dismissInterceptionBanner() {
        _interceptedPackage.value = null
    }

    fun saveAlarm(schedule: SunriseSchedule) {
        if (_alarmBusy.value) return
        _alarmBusy.value = true
        _alarmError.value = null
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        testSoundJob = viewModelScope.launch { soundCheck.play() }
    }

    fun cancelTestSound() {
        testSoundJob?.cancel()
        testSoundJob = null
        soundCheck.close()
    }

    fun confirmSound() {
        viewModelScope.launch { soundCheck.confirm() }
    }

    fun acknowledgeLimitations() {
        viewModelScope.launch { preferences.acknowledgeLimitations() }
    }

    suspend fun saveCallback(label: String, endpoint: String, actionUrl: String?): String {
        _conditionBusy.value = true
        _conditionMessage.value = null
        return try {
            wakeConditionSetup.save(label, endpoint, actionUrl).callbackId
        } finally {
            _conditionBusy.value = false
        }
    }

    fun verifyCondition(callbackId: String?) {
        if (callbackId == null || _conditionBusy.value) return
        viewModelScope.launch {
            _conditionBusy.value = true
            _conditionMessage.value = "Testing the connection…"
            try {
                _conditionMessage.value = if (wakeConditionSetup.verify(callbackId)) {
                    null
                } else {
                    "Your check-in changed. Test the connection again."
                }
            } catch (_: Exception) {
                _conditionMessage.value =
                    "Couldn’t connect. Ask the person running your service to check its setup, then try again."
            } finally {
                _conditionBusy.value = false
            }
        }
    }

    fun markConditionExported(callbackId: String) {
        _exportedVerifierId.value = callbackId
    }

    fun chooseWelcome(start: Boolean) {
        viewModelScope.launch {
            if (start) firstUsePreferences.chooseContinue() else firstUsePreferences.chooseNotNow()
        }
    }

    fun prepareDraft(draft: AlarmSetupDraft) {
        viewModelScope.launch { firstUsePreferences.saveDraft(draft) }
    }

    fun clearDraft() {
        viewModelScope.launch { runCatching { firstUsePreferences.clearDraft() } }
    }

    fun restoreRecoveryKey(encodedKey: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    accountRecovery.restore(encodedKey)
                }
            }.onSuccess { state ->
                _accountRecoveryState.value = state
                preferences.setAccountEnrolled(true)
                onResult(true)
            }.onFailure {
                onResult(false)
            }
        }
    }

    fun acknowledgeRecoveryKey() {
        viewModelScope.launch {
            accountRecovery.acknowledgeSavedRecoveryKey()
            _accountRecoveryState.value = AccountRecoveryState.Ready
            preferences.setAccountEnrolled(true)
        }
    }

    fun toggleChosenApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = enforcementPreferences.readChosenApps().toMutableSet()
            if (enabled) current.add(packageName) else current.remove(packageName)
            enforcementPreferences.setChosenApps(current)
        }
    }

    fun startEnforcementSession(purpose: EnforcementPurpose, durationMinutes: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val elapsed = SystemClock.elapsedRealtime()
            val session = EnforcementSession(
                sessionId = UUID.randomUUID().toString(),
                purpose = purpose,
                startEpochMs = now,
                elapsedRealtimeStartMs = elapsed,
                durationMinutes = durationMinutes,
                wallClockExpiryEpochMs = now + (durationMinutes * 60 * 1000L),
                chosenAppPackages = enforcementPreferences.readChosenApps(),
                consentRevision = 1,
                state = EnforcementSessionState.ACTIVE,
            )
            enforcementPreferences.saveSession(session)
        }
    }

    fun emergencyStopEnforcement() {
        viewModelScope.launch {
            CurfewAccessibilityService.disarmActiveSession()
            enforcementPreferences.emergencyStop("user_stop")
        }
    }

    override fun onCleared() {
        cancelTestSound()
        super.onCleared()
    }
}
