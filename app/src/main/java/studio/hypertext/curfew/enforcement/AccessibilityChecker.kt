package studio.hypertext.curfew.enforcement

/**
 * Interface abstracting accessibility service status check for testability.
 */
fun interface AccessibilityChecker {
    fun isEnabled(): Boolean
}
