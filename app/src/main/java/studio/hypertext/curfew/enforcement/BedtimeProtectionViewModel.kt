package studio.hypertext.curfew.enforcement

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

import dagger.hilt.android.lifecycle.HiltViewModel
import studio.hypertext.curfew.di.IoDispatcher
import javax.inject.Inject

data class BedtimeProtectionUiState(
    val session: EnforcementSession? = null,
    val chosenApps: Set<String> = emptySet(),
    val isAccessibilityReady: Boolean = false,
    val interceptedPackage: String? = null,
)

/**
 * Screen-level ViewModel for the Bedtime & Focus Interception screen.
 * Pushes bedtime protection state down directly to the feature.
 */
@HiltViewModel
class BedtimeProtectionViewModel @Inject constructor(
    private val enforcementPreferences: EnforcementPreferences,
    private val accessibilityChecker: AccessibilityChecker,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    constructor(context: Context, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : this(
        enforcementPreferences = EnforcementPreferences(context),
        accessibilityChecker = AccessibilityChecker { AccessibilityServiceStatus.isEnabled(context) },
        ioDispatcher = ioDispatcher,
    )

    private val _interceptedPackage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BedtimeProtectionUiState> = combine(
        enforcementPreferences.sessionFlow,
        enforcementPreferences.chosenAppsFlow,
        _interceptedPackage,
    ) { session, chosenApps, intercepted ->
        BedtimeProtectionUiState(
            session = session,
            chosenApps = chosenApps,
            isAccessibilityReady = accessibilityChecker.isEnabled(),
            interceptedPackage = intercepted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BedtimeProtectionUiState(),
    )

    fun setInterceptedPackage(packageName: String?) {
        _interceptedPackage.value = packageName
    }

    fun dismissInterceptionBanner() {
        _interceptedPackage.value = null
    }

    fun toggleChosenApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val current = enforcementPreferences.readChosenApps().toMutableSet()
            if (enabled) current.add(packageName) else current.remove(packageName)
            enforcementPreferences.setChosenApps(current)
        }
    }

    fun startEnforcementSession(purpose: EnforcementPurpose, durationMinutes: Int) {
        viewModelScope.launch(ioDispatcher) {
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

    fun emergencyStop() {
        viewModelScope.launch(ioDispatcher) {
            CurfewAccessibilityService.disarmActiveSession()
            enforcementPreferences.emergencyStop("user_stop")
        }
    }
}
