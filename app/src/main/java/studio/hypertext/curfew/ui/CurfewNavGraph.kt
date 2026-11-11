package studio.hypertext.curfew.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay

/**
 * Encapsulated Navigation3 NavDisplay graph dispatching to modular feature routes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CurfewNavGraph(
    appState: CurfewAppState,
    state: CurfewUiState,
    actions: CurfewUiActions,
    compactKeyboard: Boolean,
    taskTopBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    SharedTransitionLayout {
        val sharedScope = this
        NavDisplay(
            backStack = appState.backStack,
            transitionSpec = {
                (slideInHorizontally(spatial) { it / 8 } + fadeIn(effects)) togetherWith
                    (slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects))
            },
            popTransitionSpec = {
                (slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects)) togetherWith
                    (slideOutHorizontally(spatial) { it / 8 } + fadeOut(effects))
            },
            onBack = { appState.back(state) },
            modifier = modifier.fillMaxSize(),
            entryProvider = { destination ->
                NavEntry(destination) {
                    CompositionLocalProvider(
                        LocalCurfewMotion provides CurfewMotionScope(sharedScope, LocalNavAnimatedContentScope.current),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .semantics {
                                    paneTitle = when (destination) {
                                        CurfewDestination.Daily -> "Alarm"
                                        CurfewDestination.Readiness -> "Alarm setup"
                                        CurfewDestination.Edit -> "Wake time"
                                        CurfewDestination.CheckIn -> "Check-in"
                                        CurfewDestination.Review -> "Your alarm"
                                        CurfewDestination.Settings -> "Settings"
                                        CurfewDestination.Account -> "Account"
                                        CurfewDestination.Enforcement -> "Bedtime & focus"
                                        CurfewDestination.Welcome -> "Welcome to Curfew"
                                    }
                                },
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            if (destination != CurfewDestination.Readiness) {
                                AlarmSoundCheckDialog(
                                    state.soundCheckState,
                                    actions.onConfirmSound,
                                    actions.onPlayTestSound,
                                    actions.onCancelSound,
                                )
                            }
                            when (destination) {
                                CurfewDestination.Welcome -> WelcomeRoute(appState)
                                CurfewDestination.Daily -> DailyRoute(appState, state, actions)
                                CurfewDestination.Edit -> EditRoute(appState, state, actions)
                                CurfewDestination.Readiness -> ReadinessRoute(appState, state, actions)
                                CurfewDestination.CheckIn -> CheckInRoute(
                                    appState = appState,
                                    state = state,
                                    actions = actions,
                                    inlineHeader = if (compactKeyboard) taskTopBar else null,
                                )
                                CurfewDestination.Review -> ReviewRoute(appState, state, actions)
                                CurfewDestination.Settings -> SettingsRoute(appState, state)
                                CurfewDestination.Enforcement -> BedtimeRoute(state, actions)
                                CurfewDestination.Account -> AccountRoute(state, actions)
                            }
                        }
                    }
                }
            },
        )
    }
}
