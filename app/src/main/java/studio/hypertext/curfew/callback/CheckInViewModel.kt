package studio.hypertext.curfew.callback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import studio.hypertext.curfew.R
import studio.hypertext.curfew.checkin.PlaceCheckInRepository
import studio.hypertext.curfew.checkin.PlaceCheckInSetupAccess

internal data class CheckInUiState(
    val busy: Boolean = false,
    val message: Int? = null,
    val exportedId: String? = null,
)

/** Owned by the check-in navigation entry. */
@HiltViewModel
internal class CheckInViewModel @Inject constructor(
    private val setup: AndroidWakeConditionSetup,
    places: PlaceCheckInRepository,
) : ViewModel() {
    val placeSetup: PlaceCheckInSetupAccess = places
    private val mutableState = MutableStateFlow(CheckInUiState())
    val state = mutableState.asStateFlow()

    suspend fun saveCallback(label: String, endpoint: String, actionUrl: String?): String {
        mutableState.update { it.copy(busy = true, message = null) }
        return try {
            setup.save(label, endpoint, actionUrl).callbackId
        } finally {
            mutableState.update { it.copy(busy = false) }
        }
    }

    fun verifyCondition(callbackId: String?) {
        if (callbackId == null || state.value.busy) return
        mutableState.update { it.copy(busy = true, message = R.string.check_in_testing) }
        viewModelScope.launch {
            try {
                val verified = setup.verify(callbackId)
                mutableState.update { it.copy(message = R.string.check_in_selection_changed.takeUnless { verified }) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(message = R.string.check_in_test_failed) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun exportSetupFile(callbackId: String, write: suspend (String) -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                write(Json.encodeToString(setup.provision(callbackId)))
                mutableState.update { it.copy(exportedId = callbackId, message = R.string.check_in_setup_file_saved) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(message = R.string.check_in_setup_file_failed) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
