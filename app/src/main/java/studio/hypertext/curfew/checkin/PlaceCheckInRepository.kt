package studio.hypertext.curfew.checkin

import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.SunriseScheduleLock
import studio.hypertext.curfew.di.IoDispatcher
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.security.SecretStore
import javax.inject.Inject
import javax.inject.Singleton

/** Reports whether the local alarm stopped and whether the next alarm needs attention. */
enum class PlaceApprovalResult { CHECKED_IN, NOT_ACTIVE, NEXT_ALARM_UNAVAILABLE }

/** Status of future scheduling after today's check-in has already committed. */
internal enum class PlaceAlarmCompletion { COMPLETE, NEXT_ALARM_UNAVAILABLE }

/** Coordinates place-code changes with the alarm schedule and its recovery path. */
internal interface PlaceCheckInAlarmLifecycle {
    /** Disable any future alarm bound to the code before running the revocation. */
    suspend fun revokeCondition(id: String, revoke: suspend () -> Unit)

    /** Finish a committed occurrence and arrange a retry if the next alarm cannot be restored. */
    suspend fun finishOccurrence(campaignId: String): PlaceAlarmCompletion
}

/** Optionally publishes a local success to the account's other devices. */
internal fun interface PlaceCheckInPublisher {
    suspend fun publish(terminal: AlarmCampaignState.Satisfied)
}

/**
 * Stores place-code drafts and enrollments, and validates scans against the
 * code bound to an active alarm. The local terminal write happens before
 * schedule completion or optional account sync.
 */
@Singleton
class PlaceCheckInRepository @Inject internal constructor(
    private val secrets: SecretStore,
    private val preferences: CurfewPreferences,
    private val cameraChecker: CameraAvailabilityChecker,
    private val runtime: AlarmRuntimeRepository,
    private val alarms: PlaceCheckInAlarmLifecycle,
    private val publisher: PlaceCheckInPublisher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaceCheckInSetupAccess {
    override fun cameraReady(): Boolean = cameraChecker.isCameraReady()

    override suspend fun draft(): PlaceDraft? = withContext(ioDispatcher) {
        secrets.get(DRAFT)?.let { bytes ->
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            PlaceDraft(decode(json), json.getString("payload"), json.optBoolean("exported", false))
        }
    }

    override suspend fun begin(label: String): PlaceDraft = withContext(ioDispatcher) {
        SunriseScheduleLock.serialized {
            PlaceCheckIn.create(label).also {
                secrets.put(DRAFT, encode(it.enrollment).put("payload", it.payload).toString().toByteArray())
            }
        }
    }

    override suspend fun markExported(id: String) = withContext(ioDispatcher) {
        SunriseScheduleLock.serialized {
            val pending = requireNotNull(draft())
            require(pending.enrollment.id == id) { "The code was replaced while saving." }
            secrets.put(DRAFT, encode(pending.enrollment).put("payload", pending.payload).put("exported", true).toString().toByteArray())
        }
    }

    override suspend fun cancelDraft() = withContext(ioDispatcher) {
        SunriseScheduleLock.serialized { secrets.delete(DRAFT) }
    }

    override suspend fun enroll(draftId: String, scanned: String): PlaceEnrollment = withContext(ioDispatcher) {
        SunriseScheduleLock.serialized {
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

    override suspend fun find(id: String): PlaceEnrollment? = withContext(ioDispatcher) {
        secrets.get(key(id))?.let { decode(JSONObject(it.toString(Charsets.UTF_8))) }
    }

    override suspend fun remove(id: String) = withContext(ioDispatcher) {
        // Disable future delivery before revoking. The scheduler preserves a ringing gate.
        alarms.revokeCondition(id) {
            secrets.delete(key(id))
            // Selecting a different connection must never be cleared by a late removal.
            preferences.clearCallbackIfSelected(id)
        }
    }

    /** The result reports local state only; it is never a remote acknowledgment. */
    suspend fun approve(campaignId: String, scanned: String): PlaceApprovalResult = withContext(ioDispatcher) {
        var released: AlarmCampaignState.Satisfied? = null
        SunriseScheduleLock.serialized {
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
            val completion = alarms.finishOccurrence(campaignId)
            publisher.publish(terminal)
            when (completion) {
                PlaceAlarmCompletion.COMPLETE -> PlaceApprovalResult.CHECKED_IN
                PlaceAlarmCompletion.NEXT_ALARM_UNAVAILABLE -> PlaceApprovalResult.NEXT_ALARM_UNAVAILABLE
            }
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
