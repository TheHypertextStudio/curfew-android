package studio.hypertext.curfew.ui

import android.content.res.Configuration
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

private const val WIDE_WINDOW_DP = 600
private const val NAV_RAIL_MIN_HEIGHT_DP = 480
private const val INLINE_HEADER_MAX_HEIGHT_DP = 200

@Composable
internal fun CurfewApp(
    navigation: CurfewNavigationState,
    onBack: () -> Unit,
    onEditAlarm: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable (CurfewDestination, inlineHeader: (@Composable () -> Unit)?) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= WIDE_WINDOW_DP
    val useRail = wide && configuration.screenHeightDp >= NAV_RAIL_MIN_HEIGHT_DP
    val useBottomBar = wide && !useRail && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val destination = navigation.currentDestination
    val showChrome = destination != CurfewDestination.Welcome
    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.getBottom(density)
    val availableHeight = with(density) { (LocalWindowInfo.current.containerSize.height - imeHeight).toDp() }
    val compactKeyboard = destination == CurfewDestination.CheckIn && imeHeight > 0 && availableHeight < INLINE_HEADER_MAX_HEIGHT_DP.dp
    val topBar: @Composable () -> Unit = {
        CurfewTopBar(
            currentDestination = destination,
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            showSettingsAction = !useRail && !useBottomBar,
        )
    }

    Row(Modifier.fillMaxSize()) {
        if (useRail && showChrome) {
            CurfewNavigationRail(destination, onNavigate = navigation::navigate, onEditAlarm = onEditAlarm)
        }
        Scaffold(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentWindowInsets = if (showChrome) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0),
            topBar = { if (!compactKeyboard) topBar() },
            bottomBar = {
                if (useBottomBar && showChrome && !compactKeyboard) {
                    CurfewNavigationBar(destination, onNavigate = navigation::navigate)
                }
            },
        ) { padding ->
            CurfewNavGraph(
                navigation = navigation,
                onBack = onBack,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) { entry ->
                content(entry, topBar.takeIf { compactKeyboard && entry == CurfewDestination.CheckIn })
            }
        }
    }
}
