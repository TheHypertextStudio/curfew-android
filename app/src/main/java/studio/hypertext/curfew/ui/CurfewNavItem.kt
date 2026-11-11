package studio.hypertext.curfew.ui

import androidx.annotation.DrawableRes
import studio.hypertext.curfew.R

/**
 * Top-level destination item specification for primary navigation.
 */
data class CurfewNavItem(
    val destination: CurfewDestination,
    val label: String,
    @DrawableRes val iconRes: Int,
)

val PrimaryNavItems = listOf(
    CurfewNavItem(CurfewDestination.Daily, "Alarm", R.drawable.ic_alarm_rounded),
    CurfewNavItem(CurfewDestination.Enforcement, "Bedtime", R.drawable.ic_lock_rounded),
    CurfewNavItem(CurfewDestination.Settings, "Settings", R.drawable.ic_settings_rounded),
    CurfewNavItem(CurfewDestination.Account, "Account", R.drawable.ic_link_rounded),
)
