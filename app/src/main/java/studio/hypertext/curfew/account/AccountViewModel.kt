package studio.hypertext.curfew.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.R
import studio.hypertext.curfew.di.IoDispatcher
import studio.hypertext.curfew.persistence.CurfewPreferences

internal fun interface AccountSignIn {
    suspend fun exchange(callbackUrl: String): AccountRecoveryState
}

internal data class AccountUiState(
    val accountEnrolled: Boolean = false,
    val accountSignedIn: Boolean = false,
    val recoveryState: AccountRecoveryState? = null,
)

/** Activity lifetime so browser sign-in and recovery survive route changes. */
@HiltViewModel
internal class AccountViewModel @Inject constructor(
    private val accountRecovery: AndroidAccountRecovery,
    private val preferences: CurfewPreferences,
    private val signIn: AccountSignIn,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val recovery = MutableStateFlow<AccountRecoveryState?>(null)
    private val messageChannel = Channel<Int>(Channel.BUFFERED).also { channel ->
        addCloseable { channel.close() }
    }
    val messages = messageChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Do not overwrite a newer state if an OAuth callback arrived during this read.
            val pending = try {
                withContext(ioDispatcher) { accountRecovery.pendingState() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            recovery.compareAndSet(null, pending)
        }
    }

    val state = combine(preferences.state, recovery) { prefs, recoveryState ->
        AccountUiState(
            accountEnrolled = prefs.accountEnrolled,
            accountSignedIn = prefs.accountSignedIn,
            recoveryState = recoveryState.takeIf { prefs.accountSignedIn },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    val needsRecovery = state.map { it.recoveryState != null && it.recoveryState != AccountRecoveryState.Ready }
        .distinctUntilChanged()

    fun completeSignIn(callbackUrl: String) {
        viewModelScope.launch {
            val message = try {
                val result = signIn.exchange(callbackUrl)
                if (result == AccountRecoveryState.Ready) preferences.setAccountEnrolled(true)
                recovery.value = result
                when (result) {
                    AccountRecoveryState.Ready -> R.string.account_signed_in
                    AccountRecoveryState.EnterRecoveryKey -> R.string.account_enter_recovery_key
                    is AccountRecoveryState.SaveRecoveryKey -> R.string.account_save_recovery_key
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                R.string.account_sign_in_failed
            }
            messageChannel.send(message)
        }
    }

    fun restoreRecoveryKey(encodedKey: String) {
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) { accountRecovery.restore(encodedKey) }
                preferences.setAccountEnrolled(true)
                recovery.value = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messageChannel.send(R.string.account_recovery_key_failed)
            }
        }
    }

    fun acknowledgeRecoveryKey() {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { accountRecovery.acknowledgeSavedRecoveryKey() }
                preferences.setAccountEnrolled(true)
                recovery.value = AccountRecoveryState.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messageChannel.send(R.string.account_recovery_key_save_failed)
            }
        }
    }
}
