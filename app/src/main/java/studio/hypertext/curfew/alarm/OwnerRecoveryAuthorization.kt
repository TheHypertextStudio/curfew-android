package studio.hypertext.curfew.alarm

import java.util.UUID

const val OWNER_RECOVERY_REASON_MINIMUM = 50
const val OWNER_RECOVERY_HOLD_MILLIS = 3_000L
private const val CONFIRMATION_VALIDITY_MILLIS = 300_000L

data class OwnerRecoveryRequest internal constructor(
    val requestId: String,
    val campaignId: String,
    val reason: String,
    internal val requestedAtElapsed: Long,
)

class AuthorizedOwnerRecovery internal constructor(
    val requestId: String,
    val campaignId: String,
    val reason: String,
)

class OwnerRecoveryAuthorization(private val elapsedMillis: () -> Long) {
    private data class Hold(val campaignId: String, val reason: String, val beganAt: Long)
    private var hold: Hold? = null
    private var pending: OwnerRecoveryRequest? = null

    fun beginHold(campaignId: String, reason: String) {
        cancel()
        require(campaignId.isNotBlank())
        val trimmed = reason.trim()
        require(trimmed.codePointCount(0, trimmed.length) >= OWNER_RECOVERY_REASON_MINIMUM)
        hold = Hold(campaignId, trimmed, elapsedMillis())
    }

    fun cancelHold() { hold = null }

    fun cancel() {
        hold = null
        pending = null
    }

    fun requestConfirmation(): OwnerRecoveryRequest? {
        val held = hold ?: return null
        val now = elapsedMillis()
        if (now - held.beganAt < OWNER_RECOVERY_HOLD_MILLIS) return null
        hold = null
        return OwnerRecoveryRequest(UUID.randomUUID().toString(), held.campaignId, held.reason, now)
            .also { pending = it }
    }

    fun finishConfirmation(requestId: String, confirmed: Boolean): AuthorizedOwnerRecovery? {
        val requested = pending?.takeIf { it.requestId == requestId } ?: return null
        pending = null
        val age = elapsedMillis() - requested.requestedAtElapsed
        if (!confirmed || age !in 0..CONFIRMATION_VALIDITY_MILLIS) return null
        return AuthorizedOwnerRecovery(requested.requestId, requested.campaignId, requested.reason)
    }
}
