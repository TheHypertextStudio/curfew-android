package studio.hypertext.curfew.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.di.IoDispatcher
import studio.hypertext.curfew.persistence.CurfewPreferences
import javax.inject.Inject

data class AccountUiState(
    val accountEnrolled: Boolean = false,
    val accountSignedIn: Boolean = false,
    val recoveryState: AccountRecoveryState? = null,
)

/**
 * Screen-level ViewModel for the Account & Device Enrollment feature.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRecovery: AndroidAccountRecovery,
    private val preferences: CurfewPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _accountRecoveryState = MutableStateFlow<AccountRecoveryState?>(null)

    init {
        _accountRecoveryState.value = accountRecovery.pendingState()
    }

    val uiState: StateFlow<AccountUiState> = combine(
        preferences.state,
        _accountRecoveryState,
    ) { prefState, recoveryState ->
        AccountUiState(
            accountEnrolled = prefState.accountEnrolled,
            accountSignedIn = prefState.accountSignedIn,
            recoveryState = recoveryState.takeIf { prefState.accountSignedIn },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountUiState(),
    )

    fun restoreRecoveryKey(encodedKey: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
            accountRecovery.acknowledgeSavedRecoveryKey()
            _accountRecoveryState.value = AccountRecoveryState.Ready
            preferences.setAccountEnrolled(true)
        }
    }
}
