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

/**
 * Provides the two time sources needed for bounded protection sessions and an immediate
 * in-memory disarm. Keeping these calls behind a port lets the model avoid direct Android
 * service calls.
 */
internal interface ProtectionRuntime {
    fun wallClockMillis(): Long
    fun elapsedRealtimeMillis(): Long
    fun disarm()
}

/**
 * The protection screen's current session, chosen apps, and accessibility capability. The
 * intercepted app banner belongs to the navigation entry, not this durable state.
 */
internal data class BedtimeProtectionUiState(
    val session: EnforcementSession? = null,
    val chosenApps: Set<String> = emptySet(),
    val accessibilityEnabled: Boolean = false,
)

/**
 * Owns session start and stop and edits to the chosen app list while the protection entry
 * exists. App selection is saved atomically; emergency stop disarms the service before writing
 * the cancelled state.
 */
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
