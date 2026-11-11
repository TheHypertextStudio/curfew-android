package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.R

/**
 * MD3 Expressive Navigation Rail for large screens (tablets, unfolded foldables).
 * Features vertically centered navigation items, rounded container styling, and an
 * expressive pill action button.
 */
@Composable
fun CurfewNavigationRail(
    currentDestination: CurfewDestination,
    onNavigate: (CurfewDestination) -> Unit,
    onEditAlarm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        NavigationRail(
            modifier = Modifier
                .width(112.dp)
                .fillMaxHeight(),
            containerColor = Color.Transparent,
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
            header = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                ) {
                    IconButton(
                        onClick = { onNavigate(CurfewDestination.Daily) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        CurfewHorizonMark()
                    }

                    Spacer(Modifier.height(16.dp))

                    val editAlarmLabel = stringResource(R.string.nav_edit_alarm)
                    Surface(
                        onClick = onEditAlarm,
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .semantics {
                                contentDescription = editAlarmLabel
                                role = Role.Button
                            },
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_alarm_rounded),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.pane_wake_time),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                            )
                        }
                    }
                }
            },
        ) {
            // Vertically centered navigation rail items
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryNavItems.forEach { item ->
                    val label = stringResource(item.labelRes)
                    val isSelected = currentDestination == item.destination ||
                        (item.destination == CurfewDestination.Daily &&
                            (currentDestination == CurfewDestination.Edit ||
                                currentDestination == CurfewDestination.Readiness ||
                                currentDestination == CurfewDestination.CheckIn ||
                                currentDestination == CurfewDestination.Review))

                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { onNavigate(item.destination) },
                        icon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = label
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
