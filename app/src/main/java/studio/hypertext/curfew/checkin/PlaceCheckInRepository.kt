package studio.hypertext.curfew.checkin

import android.content.Context
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.SunriseScheduler
import studio.hypertext.curfew.protocols.Result
import studio.hypertext.curfew.protocols.WakeOutcome
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore
import studio.hypertext.curfew.sync.WakeOutboxPublisher
import studio.hypertext.curfew.sync.WakeSyncScheduler

enum class PlaceApprovalResult { CHECKED_IN, NOT_ACTIVE, NEXT_ALARM_UNAVAILABLE }

class PlaceCheckInRepository(context: Context) {
    private val context = context.applicationContext
    private val secrets = AndroidKeystoreSecretStore(this.context)
    private val preferences = CurfewPreferences(this.context)

    fun cameraReady(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(context,
        android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)

    suspend fun draft(): PlaceDraft? = withContext(Dispatchers.IO) {
        secrets.get(DRAFT)?.let { bytes ->
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            PlaceDraft(decode(json), json.getString("payload"), json.optBoolean("exported", false))
        }
    }

    suspend fun begin(label: String): PlaceDraft = withContext(Dispatchers.IO) {
        SunriseScheduler.serialized {
            PlaceCheckIn.create(label).also {
                secrets.put(DRAFT, encode(it.enrollment).put("payload", it.payload).toString().toByteArray())
            }
        }
    }

    suspend fun markExported(id: String) = withContext(Dispatchers.IO) {
        SunriseScheduler.serialized {
            val pending = requireNotNull(draft())
            require(pending.enrollment.id == id) { "The code was replaced while saving." }
            secrets.put(DRAFT, encode(pending.enrollment).put("payload", pending.payload).put("exported", true).toString().toByteArray())
        }
    }

    suspend fun cancelDraft() = withContext(Dispatchers.IO) {
        SunriseScheduler.serialized { secrets.delete(DRAFT) }
    }

    suspend fun enroll(draftId: String, scanned: String): PlaceEnrollment = withContext(Dispatchers.IO) {
        SunriseScheduler.serialized {
            val pending = requireNotNull(draft()) { "Create a new code before scanning." }
            require(pending.enrollment.id == draftId) { "This setup was replaced. Scan the new code." }
            val enrollment = PlaceCheckIn.enroll(pending, scanned)
            secrets.put(key(enrollment.id), encode(enrollment).toString().toByteArray())
            preferences.selectCallback(enrollment.id)
            check(preferences.markCallbackVerifiedIfSelected(enrollment.id))
            secrets.delete(DRAFT)
            enrollment
        }
    }

    suspend fun find(id: String): PlaceEnrollment? = withContext(Dispatchers.IO) {
        secrets.get(key(id))?.let { decode(JSONObject(it.toString(Charsets.UTF_8))) }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        // Disable future delivery before revoking. The scheduler preserves a ringing gate.
        SunriseScheduler(context).revokeCondition(id) {
            secrets.delete(key(id))
            // Selecting a different connection must never be cleared by a late removal.
            preferences.clearCallbackIfSelected(id)
        }
    }

    /** The result reports local state only; it is never a remote acknowledgment. */
    suspend fun approve(campaignId: String, scanned: String): PlaceApprovalResult = withContext(Dispatchers.IO) {
        val runtime = AlarmRuntimeRepository(context)
        var released: AlarmCampaignState.Satisfied? = null
        SunriseScheduler.serialized {
            runtime.updateExisting(campaignId) { snapshot ->
                val current = snapshot.state
                if (current is AlarmCampaignState.Satisfied || current is AlarmCampaignState.Overridden) {
                    return@updateExisting current
                }
                val binding = requireNotNull(runtime.callbackId(campaignId))
                val enrollment = find(binding)
                val terminal = PlaceCheckIn.approve(enrollment, scanned, campaignId, current, Instant.now())
                (terminal as AlarmCampaignState.Satisfied).also { released = it }
            }
        }
        val terminal = released ?: return@withContext PlaceApprovalResult.NOT_ACTIVE
        // Once committed, a scanner disappearing must not cancel local completion.
        withContext(kotlinx.coroutines.NonCancellable) {
        // The terminal Room write precedes all scheduling and network side effects.
        val completed = try {
            SunriseScheduler(context).completeOccurrence(campaignId)
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CallbackPollScheduler.schedule(context, campaignId)
            false
        }
        try {
            val device = NativeDeviceProofAuthenticator(context).deviceId()
            WakeOutboxPublisher(context).publish(WakeOutcome(campaignId = campaignId, releasedAt = terminal.satisfiedAt.toString(), result = Result.Satisfied, satisfyingDeviceId = device), writerDeviceId = device, version = 1)
            WakeSyncScheduler.accelerate(context)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            android.util.Log.w("PlaceCheckIn", "Local check-in saved; account sync needs attention")
        }
        if (completed) PlaceApprovalResult.CHECKED_IN else PlaceApprovalResult.NEXT_ALARM_UNAVAILABLE
        }
    }

    private fun encode(enrollment: PlaceEnrollment) = JSONObject()
        .put("version", 1).put("id", enrollment.id).put("label", enrollment.label).put("digest", enrollment.digest)

    private fun decode(json: JSONObject): PlaceEnrollment {
        require(json.getInt("version") == 1) { "This code needs a newer Curfew version." }
        return PlaceEnrollment(json.getString("id"), json.getString("label"), json.getString("digest"))
    }

    private fun key(id: String) = "place-check-in:$id"
    private companion object { const val DRAFT = "place-check-in:draft" }
}
