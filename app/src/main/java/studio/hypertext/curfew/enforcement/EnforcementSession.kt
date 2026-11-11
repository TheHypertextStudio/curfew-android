package studio.hypertext.curfew.enforcement

enum class EnforcementPurpose {
    BEDTIME,
    PROTECTED_WORK,
}

enum class EnforcementSessionState {
    ACTIVE,
    CANCELLED_EMERGENCY,
    EXPIRED,
    DISARMED_AFTER_REBOOT,
}

data class EnforcementSession(
    val sessionId: String,
    val purpose: EnforcementPurpose,
    val startEpochMs: Long,
    val elapsedRealtimeStartMs: Long,
    val durationMinutes: Int,
    val wallClockExpiryEpochMs: Long,
    val chosenAppPackages: Set<String>,
    val consentRevision: Int,
    val state: EnforcementSessionState,
) {
    val durationMs: Long get() = durationMinutes * 60 * 1000L

    fun isExpired(currentEpochMs: Long, currentElapsedRealtimeMs: Long): Boolean {
        if (state != EnforcementSessionState.ACTIVE) return true
        if (currentEpochMs < startEpochMs) return true // Clock rollback detected
        if (currentEpochMs >= wallClockExpiryEpochMs) return true
        val elapsedDuration = currentElapsedRealtimeMs - elapsedRealtimeStartMs
        if (elapsedDuration >= durationMs) return true
        return false
    }
}
