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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import studio.hypertext.curfew.R

/**
 * Renders the Navigation 3 back stack with transitions and screen semantics. The entry
 * decorators retain each destination's saveable state and give its ViewModels a separate
 * store.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CurfewNavGraph(
    navigation: CurfewNavigationState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (CurfewDestination) -> Unit,
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    SharedTransitionLayout {
        val sharedScope = this
        NavDisplay(
            backStack = navigation.backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = {
                (slideInHorizontally(spatial) { it / 8 } + fadeIn(effects)) togetherWith
                    (slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects))
            },
            popTransitionSpec = {
                (slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects)) togetherWith
                    (slideOutHorizontally(spatial) { it / 8 } + fadeOut(effects))
            },
            onBack = onBack,
            modifier = modifier.fillMaxSize(),
            entryProvider = { destination ->
                NavEntry(destination) {
                    CompositionLocalProvider(
                        LocalCurfewMotion provides CurfewMotionScope(sharedScope, LocalNavAnimatedContentScope.current),
                    ) {
                        val title = stringResource(destination.paneTitle)
                        Box(
                            Modifier.fillMaxSize().semantics { paneTitle = title },
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            content(destination)
                        }
                    }
                }
            },
        )
    }
}

/**
 * Localized title used by navigation surfaces and accessibility to identify a destination.
 */
internal val CurfewDestination.paneTitle: Int
    get() = when (this) {
        CurfewDestination.Daily -> R.string.pane_alarm
        CurfewDestination.Readiness -> R.string.pane_alarm_setup
        CurfewDestination.Edit -> R.string.pane_wake_time
        CurfewDestination.CheckIn -> R.string.check_in_label
        CurfewDestination.Review -> R.string.pane_your_alarm
        CurfewDestination.Settings -> R.string.pane_settings
        CurfewDestination.Account -> R.string.pane_account
        CurfewDestination.Enforcement -> R.string.pane_protection
        CurfewDestination.Welcome -> R.string.pane_welcome
    }
