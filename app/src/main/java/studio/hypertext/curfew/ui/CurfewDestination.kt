package studio.hypertext.curfew.ui

/**
 * Type-safe navigation destinations for Navigation 3.
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
