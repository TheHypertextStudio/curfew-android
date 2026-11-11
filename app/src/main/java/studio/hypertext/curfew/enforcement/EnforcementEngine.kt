package studio.hypertext.curfew.enforcement

/**
 * Deterministic engine that processes window state events and coordinates
 * interception actions, debounce timing, and in-memory disarm state.
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
