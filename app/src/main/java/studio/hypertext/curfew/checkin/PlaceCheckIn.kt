package studio.hypertext.curfew.checkin

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState

data class PlaceEnrollment(val id: String, val label: String, val digest: String) {
    init {
        require(UUID.fromString(id).toString() == id) { "Code identity is damaged." }
        require(label == label.trim() && label.length in 1..60) { "Code name is damaged." }
        require(Regex("[A-Za-z0-9_-]{43}").matches(digest)) { "Code verification is damaged." }
    }
}
data class PlaceDraft(val enrollment: PlaceEnrollment, val payload: String, val exported: Boolean = false)

/** Local printed-code format. Never a Sync credential, URL, or account key. */
object PlaceCheckIn {
    private val format = Regex("curfew:place:1:[0-9a-f-]{36}:[A-Za-z0-9_-]{43}")

    fun create(label: String): PlaceDraft {
        val name = label.trim()
        require(name.length in 1..60) { "Name the place in 60 characters or fewer." }
        val id = UUID.randomUUID().toString()
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        val payload = "curfew:place:1:$id:$token"
        return PlaceDraft(PlaceEnrollment(id, name, digest(payload)), payload)
    }

    fun enroll(draft: PlaceDraft, scanned: String): PlaceEnrollment {
        require(matches(draft.enrollment, scanned)) { "Scan the code you just saved." }
        return draft.enrollment
    }

    fun approve(enrollment: PlaceEnrollment?, scanned: String, campaignId: String,
                current: AlarmCampaignState, now: Instant): AlarmCampaignState {
        require(enrollment != null && matches(enrollment, scanned)) { "This code is not enrolled." }
        require(current.campaignId == campaignId) { "This scan belongs to another alarm." }
        require(current is AlarmCampaignState.Ringing || current is AlarmCampaignState.Quiet) { "This alarm is not active." }
        return AlarmCampaignEngine().satisfy(current, now, enrollment.label)
    }

    private fun matches(enrollment: PlaceEnrollment, payload: String): Boolean {
        if (payload.length > 160 || !format.matches(payload)) return false
        val id = payload.split(':')[3]
        if (id != enrollment.id || runCatching { UUID.fromString(id).toString() }.getOrNull() != id) return false
        return MessageDigest.isEqual(enrollment.digest.toByteArray(Charsets.US_ASCII), digest(payload).toByteArray(Charsets.US_ASCII))
    }

    private fun digest(payload: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8)))
}
