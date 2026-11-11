package studio.hypertext.curfew.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import studio.hypertext.curfew.enforcement.AccessibilityChecker
import studio.hypertext.curfew.enforcement.EnforcementPreferences
import studio.hypertext.curfew.enforcement.EnforcementSessionState
import studio.hypertext.curfew.persistence.CurfewPreferences

/**
 * The three protection summaries used on Settings. Only the protection route holds the
 * editable app list and session controls.
 */
internal enum class ProtectionStatus { ACTIVE, READY, NEEDS_ATTENTION }

/**
 * The settings entry's account and protection summary. Alarm and check-in readiness come from
 * the alarm journey instead of being duplicated here.
 */
internal data class SettingsUiState(
    val accountSignedIn: Boolean = false,
    val protection: ProtectionStatus = ProtectionStatus.NEEDS_ATTENTION,
)

/**
 * Reads the status needed by Settings only. It recomputes the accessibility summary on resume
 * without creating a protection session or loading its editable app list.
 */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    preferences: CurfewPreferences,
    enforcement: EnforcementPreferences,
    accessibility: AccessibilityChecker,
) : ViewModel() {
    private val capabilityRevision = MutableStateFlow(0)

    val state = combine(preferences.state, enforcement.sessionFlow, capabilityRevision) { prefs, session, _ ->
        SettingsUiState(
            accountSignedIn = prefs.accountSignedIn,
            protection = when {
                session?.state == EnforcementSessionState.ACTIVE -> ProtectionStatus.ACTIVE
                accessibility.isEnabled() -> ProtectionStatus.READY
                else -> ProtectionStatus.NEEDS_ATTENTION
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refreshCapabilities() {
        capabilityRevision.update { it + 1 }
    }
}
