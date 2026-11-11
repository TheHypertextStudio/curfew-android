package studio.hypertext.curfew.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.account.AccountRecoveryState

/**
 * Core Composable structured with clear UI state, actions, and adaptive layouts.
 */
@Composable
fun CurfewApp(
    state: CurfewUiState,
    actions: CurfewUiActions,
    modifier: Modifier = Modifier,
    navigationState: CurfewNavigationState? = null,
    placeCheckInContent: (@Composable (() -> Unit, () -> Unit, (() -> Unit)?) -> Unit)? = null,
) {
    val appState = rememberCurfewAppState(
        state = state,
        navigationState = navigationState,
        placeCheckInContent = placeCheckInContent,
    )

    LaunchedEffect(state.accountRecoveryState) {
        if (state.accountRecoveryState is AccountRecoveryState.EnterRecoveryKey ||
            state.accountRecoveryState is AccountRecoveryState.SaveRecoveryKey
        ) {
            appState.navigate(CurfewDestination.Account)
        }
    }

    LaunchedEffect(state.openEnforcement) {
        if (state.openEnforcement) {
            appState.navigate(CurfewDestination.Enforcement)
            actions.onEnforcementOpened()
        }
    }

    LaunchedEffect(state.alarmBusy, state.alarmError, appState.saving, state.sunriseAlarm, appState.setupMode, appState.draft?.id) {
        val activated = appState.setupMode && state.sunriseAlarm?.schedule?.let { it.id == appState.draft?.id && it.enabled } == true
        if (!state.alarmBusy && activated) {
            appState.saving = false
            actions.onSetupActivated()
            appState.setupMode = false
            appState.draft = null
            appState.navigateToRoot()
        } else if (appState.saving && !state.alarmBusy) {
            if (state.alarmError != null) {
                appState.saving = false
            } else if (!appState.setupMode) {
                appState.saving = false
                appState.draft = null
            }
        }
    }

    // Responsive window layout detection
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useNavRail = widthDp >= 600 && heightDp >= 480
    val useLandscapeNavBar = isLandscape && heightDp < 480 && widthDp >= 600

    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.getBottom(density)
    val availableHeight = with(density) { (LocalWindowInfo.current.containerSize.height - imeHeight).toDp() }
    val compactKeyboard = appState.currentDestination == CurfewDestination.CheckIn && imeHeight > 0 && availableHeight < 200.dp

    val topBarComposable: @Composable () -> Unit = {
        if (!compactKeyboard) {
            CurfewTopBar(
                currentDestination = appState.currentDestination,
                onBack = { appState.back(state) },
                onOpenSettings = {
                    appState.setupMode = false
                    appState.navigate(CurfewDestination.Settings)
                },
                showSettingsAction = !useNavRail && !useLandscapeNavBar,
            )
        }
    }

    if (useNavRail) {
        Row(modifier = modifier.fillMaxSize()) {
            if (appState.currentDestination != CurfewDestination.Welcome) {
                CurfewNavigationRail(
                    currentDestination = appState.currentDestination,
                    onNavigate = { appState.navigate(it) },
                    onEditAlarm = {
                        appState.setupMode = state.sunriseAlarm == null
                        appState.navigate(CurfewDestination.Edit)
                    },
                )
            }
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentWindowInsets = if (appState.currentDestination == CurfewDestination.Welcome) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                topBar = topBarComposable,
            ) { padding ->
                CurfewNavGraph(
                    appState = appState,
                    state = state,
                    actions = actions,
                    compactKeyboard = compactKeyboard,
                    taskTopBar = topBarComposable,
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                )
            }
        }
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = if (appState.currentDestination == CurfewDestination.Welcome) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
            topBar = topBarComposable,
            bottomBar = {
                if (useLandscapeNavBar && appState.currentDestination != CurfewDestination.Welcome && !compactKeyboard) {
                    CurfewNavigationBar(
                        currentDestination = appState.currentDestination,
                        onNavigate = { appState.navigate(it) },
                    )
                }
            },
        ) { padding ->
            CurfewNavGraph(
                appState = appState,
                state = state,
                actions = actions,
                compactKeyboard = compactKeyboard,
                taskTopBar = topBarComposable,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            )
        }
    }
}
