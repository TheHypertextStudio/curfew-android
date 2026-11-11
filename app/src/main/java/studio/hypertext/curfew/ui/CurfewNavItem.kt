package studio.hypertext.curfew.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import studio.hypertext.curfew.R

/**
 * Top-level destination item specification for primary navigation.
 */
data class CurfewNavItem(
    val destination: CurfewDestination,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
)

val PrimaryNavItems = listOf(
    CurfewNavItem(CurfewDestination.Daily, R.string.pane_alarm, R.drawable.ic_alarm_rounded),
    CurfewNavItem(CurfewDestination.Enforcement, R.string.nav_bedtime, R.drawable.ic_lock_rounded),
    CurfewNavItem(CurfewDestination.Settings, R.string.pane_settings, R.drawable.ic_settings_rounded),
    CurfewNavItem(CurfewDestination.Account, R.string.pane_account, R.drawable.ic_link_rounded),
)
