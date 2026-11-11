package studio.hypertext.curfew.ui

/**
 * The app's stable navigation keys. The route string is saved with the back stack, so existing
 * values should remain readable across upgrades.
 */
enum class CurfewDestination(val route: String) {
    Daily("daily"),
    Edit("edit"),
    CheckIn("check-in"),
    Readiness("readiness"),
    Review("review"),
    Settings("settings"),
    Account("account"),
    Enforcement("enforcement"),
    Welcome("welcome");

    companion object {
        fun fromRoute(route: String): CurfewDestination =
            entries.firstOrNull { it.route == route } ?: Daily
    }
}
