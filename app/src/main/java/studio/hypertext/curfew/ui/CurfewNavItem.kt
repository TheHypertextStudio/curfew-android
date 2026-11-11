package studio.hypertext.curfew.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import studio.hypertext.curfew.R

/**
 * A primary destination with the resource IDs for its label and icon. Rail and bottom
 * navigation use the same list.
 */
data class CurfewNavItem(
    val destination: CurfewDestination,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
)

/** Destinations shown in the primary navigation controls, in their display order. */
val PrimaryNavItems = listOf(
    CurfewNavItem(CurfewDestination.Daily, R.string.pane_alarm, R.drawable.ic_alarm_rounded),
    CurfewNavItem(CurfewDestination.Enforcement, R.string.nav_bedtime, R.drawable.ic_lock_rounded),
    CurfewNavItem(CurfewDestination.Settings, R.string.pane_settings, R.drawable.ic_settings_rounded),
    CurfewNavItem(CurfewDestination.Account, R.string.pane_account, R.drawable.ic_link_rounded),
)
