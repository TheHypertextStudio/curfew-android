package studio.hypertext.curfew.persistence

sealed interface FirstUseLoadState {
    data object Loading : FirstUseLoadState
    data class Ready(val state: FirstUseState) : FirstUseLoadState
    data object Error : FirstUseLoadState
}
