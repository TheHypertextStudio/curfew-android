package studio.hypertext.curfew.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRuntimeSnapshot
import studio.hypertext.curfew.checkin.CheckInCatalog
import studio.hypertext.curfew.checkin.PlaceCheckInApprover
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.SunriseScheduler

/**
 * Computes the next display time from a persisted snapshot using current wall, elapsed, and
 * boot clocks supplied at the platform edge.
 */
internal fun interface NextAlarmTransition {
    fun at(snapshot: AlarmRuntimeSnapshot): Instant?
}

/**
 * Observes the requested occurrence, falling back to the saved schedule when none is
 * requested. It has Activity lifetime so an alarm can replace any destination, and it never
 * waits for onboarding or settings reads.
 */
@HiltViewModel
internal class RingingAlarmViewModel @Inject constructor(
    private val runtime: AlarmRuntimeRepository,
    scheduler: SunriseScheduler,
    private val checkIns: CheckInCatalog,
    private val places: PlaceCheckInApprover,
    private val nextTransition: NextAlarmTransition,
) : ViewModel() {
    private val requestedCampaign = MutableStateFlow<String?>(null)
    private val scheduledCampaign = scheduler.state.map { it?.occurrence?.campaignId }.catch { emit(null) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val presentation = requestedCampaign
        .flatMapLatest { requested -> if (requested == null) scheduledCampaign else flowOf(requested) }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                runtime.observeSnapshot(id)
                    .onEach { snapshot ->
                        when (snapshot?.state) {
                            is AlarmCampaignState.Satisfied, is AlarmCampaignState.Overridden ->
                                requestedCampaign.compareAndSet(id, null)
                            else -> Unit
                        }
                    }
                    .ringingPresentation { campaign ->
                        runtime.callbackId(campaign)?.let { checkIns.find(it) }
                    }
                    .map { alarm ->
                        if (alarm?.snapshot?.state is AlarmCampaignState.Quiet) {
                            alarm.copy(nextRingAt = nextTransition.at(alarm.snapshot))
                        } else {
                            alarm
                        }
                    }
                    .onStart { emit(null) }
                    .catch { emit(null) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), null)

    fun present(campaignId: String) {
        requestedCampaign.value = campaignId
    }

    suspend fun approvePlaceCode(campaignId: String, code: String) = places.approve(campaignId, code)
}
