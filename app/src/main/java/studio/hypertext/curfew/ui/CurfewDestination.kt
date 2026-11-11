package studio.hypertext.curfew.ui

/**
 * Type-safe navigation destinations for Navigation 3.
 */
sealed interface CurfewDestination {
    val route: String

    data object Daily : CurfewDestination { override val route = "daily" }
    data object Edit : CurfewDestination { override val route = "edit" }
    data object CheckIn : CurfewDestination { override val route = "check-in" }
    data object Readiness : CurfewDestination { override val route = "readiness" }
    data object Review : CurfewDestination { override val route = "review" }
    data object Settings : CurfewDestination { override val route = "settings" }
    data object Account : CurfewDestination { override val route = "account" }
    data object Enforcement : CurfewDestination { override val route = "enforcement" }
    data object Welcome : CurfewDestination { override val route = "welcome" }

    companion object {
        fun fromRoute(route: String): CurfewDestination = when (route) {
            "daily" -> Daily
            "edit" -> Edit
            "check-in" -> CheckIn
            "readiness" -> Readiness
            "review" -> Review
            "settings" -> Settings
            "account" -> Account
            "enforcement" -> Enforcement
            "welcome" -> Welcome
            else -> Daily
        }
    }
}
