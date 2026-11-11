package studio.hypertext.curfew.callback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import studio.hypertext.curfew.checkin.CheckInCatalog
import studio.hypertext.curfew.di.IoDispatcher
import studio.hypertext.curfew.persistence.CurfewPreferences
import javax.inject.Inject

data class CheckInUiState(
    val conditionBusy: Boolean = false,
    val conditionMessage: String? = null,
    val exportedVerifierId: String? = null,
)

/**
 * Screen-level ViewModel for the Check-In and Verification feature.
 * Owns connection verification state locally, preventing root-level recomposition churn.
 */
@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val wakeConditionSetup: AndroidWakeConditionSetup,
    private val preferences: CurfewPreferences,
    private val checkInCatalog: CheckInCatalog,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _conditionBusy = MutableStateFlow(false)
    val conditionBusy: StateFlow<Boolean> = _conditionBusy.asStateFlow()

    private val _conditionMessage = MutableStateFlow<String?>(null)
    val conditionMessage: StateFlow<String?> = _conditionMessage.asStateFlow()

    private val _exportedVerifierId = MutableStateFlow<String?>(null)
    val exportedVerifierId: StateFlow<String?> = _exportedVerifierId.asStateFlow()

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
        viewModelScope.launch(ioDispatcher) {
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
}
