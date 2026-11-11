package studio.hypertext.curfew.enforcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal interface ProtectionRuntime {
    fun wallClockMillis(): Long
    fun elapsedRealtimeMillis(): Long
    fun disarm()
}

internal data class BedtimeProtectionUiState(
    val session: EnforcementSession? = null,
    val chosenApps: Set<String> = emptySet(),
    val accessibilityEnabled: Boolean = false,
)

@HiltViewModel
internal class BedtimeProtectionViewModel @Inject constructor(
    private val preferences: EnforcementPreferences,
    private val accessibility: AccessibilityChecker,
    private val runtime: ProtectionRuntime,
) : ViewModel() {
    private val accessibilityRevision = MutableStateFlow(0)

    val state = combine(
        preferences.sessionFlow,
        preferences.chosenAppsFlow,
        accessibilityRevision,
    ) { session, apps, _ ->
        BedtimeProtectionUiState(session, apps, accessibility.isEnabled())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BedtimeProtectionUiState())

    fun refreshAccessibility() {
        accessibilityRevision.update { it + 1 }
    }

    fun toggleChosenApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch { preferences.setChosenAppEnabled(packageName, enabled) }
    }

    fun startSession(purpose: EnforcementPurpose, durationMinutes: Int) {
        viewModelScope.launch {
            val now = runtime.wallClockMillis()
            preferences.saveSession(
                EnforcementSession(
                    sessionId = UUID.randomUUID().toString(),
                    purpose = purpose,
                    startEpochMs = now,
                    elapsedRealtimeStartMs = runtime.elapsedRealtimeMillis(),
                    durationMinutes = durationMinutes,
                    wallClockExpiryEpochMs = now + Duration.ofMinutes(durationMinutes.toLong()).toMillis(),
                    chosenAppPackages = preferences.readChosenApps(),
                    consentRevision = INITIAL_CONSENT_REVISION,
                    state = EnforcementSessionState.ACTIVE,
                ),
            )
        }
    }

    fun emergencyStop() {
        viewModelScope.launch {
            runtime.disarm()
            preferences.emergencyStop()
        }
    }

    private companion object {
        const val INITIAL_CONSENT_REVISION = 1
    }
}
