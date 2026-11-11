package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.R

/**
 * Top app bar providing hierarchical title, branding, back navigation, and actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CurfewTopBar(
    currentDestination: CurfewDestination,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    showSettingsAction: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (currentDestination == CurfewDestination.Welcome) return

    val title = when (currentDestination) {
        CurfewDestination.Edit -> "Wake time"
        CurfewDestination.Readiness -> "Alarm setup"
        CurfewDestination.CheckIn -> "Check-in"
        CurfewDestination.Settings -> "Settings"
        CurfewDestination.Account -> "Account"
        CurfewDestination.Review -> "Your alarm"
        CurfewDestination.Enforcement -> "Bedtime & focus"
        else -> "Curfew"
    }

    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        title = {
            if (currentDestination == CurfewDestination.Daily) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CurfewHorizonMark()
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        },
        navigationIcon = {
            if (currentDestination != CurfewDestination.Daily) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back_rounded), "Back")
                }
            }
        },
        actions = {
            if (currentDestination == CurfewDestination.Daily && showSettingsAction) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = {
                        PlainTooltip(
                            Modifier.semantics {
                                liveRegion = LiveRegionMode.Assertive
                                paneTitle = "Settings"
                            },
                        ) { Text("Settings") }
                    },
                    state = rememberTooltipState(),
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenSettings,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_settings_rounded), "Settings")
                    }
                }
            }
        },
    )
}
