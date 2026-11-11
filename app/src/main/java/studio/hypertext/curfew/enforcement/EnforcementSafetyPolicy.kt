package studio.hypertext.curfew.enforcement

/**
 * Policy result for one foreground event. Allow is the safe default when protection is
 * inactive, uncertain, or exempt.
 */
enum class InterceptionDecision {
    INTERCEPT,
    ALLOW,
}

/**
 * Snapshot of platform facts relevant to safe interception, such as the active call, launcher,
 * dialer, or navigation app. The pure policy reads it without asking Android directly.
 */
data class SafetyContext(
    val curfewPackage: String,
    val defaultDialerPackage: String? = null,
    val defaultSmsPackage: String? = null,
    val homePackages: Set<String> = emptySet(),
    val activeNavigationPackages: Set<String> = emptySet(),
    val autofillServicePackages: Set<String> = emptySet(),
    val isInCall: Boolean = false,
)

/**
 * Decides whether a bounded active session may intercept an explicitly selected package.
 * Exempt system apps, calls, expired sessions, and unknown packages are always allowed.
 */
object EnforcementSafetyPolicy {

    /**
     * Permanent system & emergency safety packages that must never be intercepted
     * under any circumstances, preserving user safety and system control.
     */
    private val CORE_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
    )

    fun isPackageExempt(
        packageName: String,
        safetyContext: SafetyContext,
    ): Boolean {
        if (packageName == safetyContext.curfewPackage) return true
        if (packageName.startsWith("studio.hypertext.curfew")) return true
        if (packageName in CORE_SYSTEM_PACKAGES) return true
        if (safetyContext.defaultDialerPackage != null && packageName == safetyContext.defaultDialerPackage) return true
        if (safetyContext.defaultSmsPackage != null && packageName == safetyContext.defaultSmsPackage) return true
        if (packageName in safetyContext.homePackages) return true
        if (packageName in safetyContext.activeNavigationPackages) return true
        if (packageName in safetyContext.autofillServicePackages) return true
        return false
    }

    fun evaluate(
        packageName: String,
        session: EnforcementSession?,
        safetyContext: SafetyContext,
        currentEpochMs: Long,
        currentElapsedRealtimeMs: Long,
    ): InterceptionDecision {
        if (session == null || session.state != EnforcementSessionState.ACTIVE) {
            return InterceptionDecision.ALLOW
        }

        // Active phone call always suspends interruption
        if (safetyContext.isInCall) {
            return InterceptionDecision.ALLOW
        }

        // Check expiration and rollback bounds
        if (session.isExpired(currentEpochMs, currentElapsedRealtimeMs)) {
            return InterceptionDecision.ALLOW
        }

        // Check safety allowlist
        if (isPackageExempt(packageName, safetyContext)) {
            return InterceptionDecision.ALLOW
        }

        // Only explicitly chosen entertainment apps are intercepted; unknown packages are allowed
        if (packageName !in session.chosenAppPackages) {
            return InterceptionDecision.ALLOW
        }

        return InterceptionDecision.INTERCEPT
    }
}
