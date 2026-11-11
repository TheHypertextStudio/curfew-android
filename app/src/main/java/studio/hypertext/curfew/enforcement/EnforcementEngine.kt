package studio.hypertext.curfew.enforcement

/**
 * Applies the pure protection policy to foreground events and adds runtime disarm and
 * debounce. The engine invokes interception only after the safety policy permits it.
 */
class EnforcementEngine(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    var isDisarmedInMemory: Boolean = false
        private set

    private var lastInterceptElapsedRealtimeMs: Long = 0L

    fun emergencyDisarmInMemory() {
        isDisarmedInMemory = true
    }

    fun resetDisarmedState() {
        isDisarmedInMemory = false
    }

    fun processEvent(
        packageName: String,
        session: EnforcementSession?,
        safetyContext: SafetyContext,
        currentEpochMs: Long,
        currentElapsedRealtimeMs: Long,
        onIntercept: () -> Unit,
    ): InterceptionDecision {
        if (isDisarmedInMemory) {
            return InterceptionDecision.ALLOW
        }

        val policyDecision = EnforcementSafetyPolicy.evaluate(
            packageName = packageName,
            session = session,
            safetyContext = safetyContext,
            currentEpochMs = currentEpochMs,
            currentElapsedRealtimeMs = currentElapsedRealtimeMs,
        )

        if (policyDecision != InterceptionDecision.INTERCEPT) {
            return InterceptionDecision.ALLOW
        }

        // Check debounce cooldown to prevent rapid UI loops
        val timeSinceLastIntercept = currentElapsedRealtimeMs - lastInterceptElapsedRealtimeMs
        if (lastInterceptElapsedRealtimeMs > 0 && timeSinceLastIntercept in 0 until cooldownMs) {
            return InterceptionDecision.ALLOW
        }

        lastInterceptElapsedRealtimeMs = currentElapsedRealtimeMs
        onIntercept()
        return InterceptionDecision.INTERCEPT
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 1500L
    }
}
