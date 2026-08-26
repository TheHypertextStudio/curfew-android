package studio.hypertext.curfew.platform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.alarm.AlarmRuntimeSnapshot
import studio.hypertext.curfew.persistence.AlarmCampaignEntity
import studio.hypertext.curfew.persistence.CurfewDatabase

class AlarmRuntimeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = CurfewDatabase.open(appContext).alarmCampaignDao()

    suspend fun create(
        campaignId: String,
        startsAt: Instant,
        policy: AlarmRecurrencePolicy,
        selectedDeviceIds: Set<String>,
        callbackId: String? = null,
    ): AlarmCampaignState.Scheduled {
        val state = AlarmCampaignState.Scheduled(
            campaignId = campaignId,
            startsAt = startsAt,
            policy = policy,
            selectedDeviceIds = selectedDeviceIds,
        )
        save(state, callbackId = callbackId)
        return state
    }

    suspend fun load(campaignId: String): AlarmCampaignState? = withContext(Dispatchers.IO) {
        dao.find(campaignId)?.let(::decode)
    }

    suspend fun save(
        state: AlarmCampaignState,
        writerCounter: Long? = null,
        callbackId: String? = null,
    ) =
        withContext(Dispatchers.IO) {
            val existing = dao.find(state.campaignId)
            val existingCallbackId = callbackId ?: existing?.callbackId
            dao.upsert(
                AlarmCampaignEntity(
                    campaignId = state.campaignId,
                    protocolJson = encode(state).toString(),
                    state = state.storageName(),
                    // This legacy Room column remains only to read databases created by 0.2.
                    // Long.MAX_VALUE means that persisted state has no release deadline.
                    finalDeadlineAt = Long.MAX_VALUE,
                    persistedAtWall = System.currentTimeMillis(),
                    persistedAtElapsed = SystemClock.elapsedRealtime(),
                    bootId = bootId(),
                    writerCounter = writerCounter ?: existing?.writerCounter ?: 0,
                    callbackId = existingCallbackId,
                ),
            )
        }

    suspend fun pending(): List<AlarmCampaignState> = withContext(Dispatchers.IO) {
        dao.pending().map(::decode)
    }

    suspend fun pendingSnapshots(): List<AlarmRuntimeSnapshot> = withContext(Dispatchers.IO) {
        dao.pending().map { entity ->
            AlarmRuntimeSnapshot(
                state = decode(entity),
                persistedAtWall = Instant.ofEpochMilli(entity.persistedAtWall),
                persistedAtElapsedMillis = entity.persistedAtElapsed,
                bootId = entity.bootId,
            )
        }
    }

    fun currentBootId(): String = bootId()

    suspend fun callbackId(campaignId: String): String? = withContext(Dispatchers.IO) {
        dao.find(campaignId)?.callbackId
    }

    suspend fun statusVersion(campaignId: String): Long = withContext(Dispatchers.IO) {
        dao.find(campaignId)?.writerCounter ?: 0L
    }

    private fun encode(state: AlarmCampaignState): JSONObject = JSONObject().apply {
        put("campaignId", state.campaignId)
        put("kind", state.storageName())
        put("ringMillis", state.policy.ringDuration.toMillis())
        put("quietMillis", state.policy.quietDuration.toMillis())
        put("devices", JSONArray(state.selectedDeviceIds.toList()))
        when (state) {
            is AlarmCampaignState.Scheduled -> put("startsAt", state.startsAt.toString())
            is AlarmCampaignState.Ringing -> {
                put("attempt", state.attempt)
                put("ringStartedAt", state.ringStartedAt.toString())
                put("ringEndsAt", state.ringEndsAt.toString())
            }
            is AlarmCampaignState.Quiet -> {
                put("completedAttempt", state.completedAttempt)
                put("nextAttemptAt", state.nextAttemptAt.toString())
            }
            is AlarmCampaignState.Satisfied -> {
                put("satisfiedAt", state.satisfiedAt.toString())
                put("conditionLabel", state.conditionLabel)
            }
            is AlarmCampaignState.Overridden -> {
                put("overriddenAt", state.overriddenAt.toString())
                put("overrideId", state.overrideId)
                put("validUntil", state.validUntil.toString())
            }
        }
    }

    private fun decode(entity: AlarmCampaignEntity): AlarmCampaignState {
        val json = JSONObject(entity.protocolJson)
        val policy = AlarmRecurrencePolicy(
            ringDuration = Duration.ofMillis(json.getLong("ringMillis")),
            quietDuration = Duration.ofMillis(json.getLong("quietMillis")),
        )
        val devices = json.getJSONArray("devices").let { array ->
            buildSet { repeat(array.length()) { add(array.getString(it)) } }
        }
        val id = json.getString("campaignId")
        return when (json.getString("kind")) {
            "scheduled" -> AlarmCampaignState.Scheduled(
                id, Instant.parse(json.getString("startsAt")), policy, devices,
            )
            "ringing" -> AlarmCampaignState.Ringing(
                id,
                json.getInt("attempt"),
                Instant.parse(json.getString("ringStartedAt")),
                Instant.parse(json.getString("ringEndsAt")),
                policy,
                devices,
            )
            "quiet" -> AlarmCampaignState.Quiet(
                id,
                json.getInt("completedAttempt"),
                Instant.parse(json.getString("nextAttemptAt")),
                policy,
                devices,
            )
            "satisfied" -> AlarmCampaignState.Satisfied(
                id,
                Instant.parse(json.getString("satisfiedAt")),
                json.getString("conditionLabel"),
                devicesToRelease = devices,
                policy = policy,
                selectedDeviceIds = devices,
            )
            "overridden" -> AlarmCampaignState.Overridden(
                id,
                Instant.parse(json.getString("overriddenAt")),
                json.getString("overrideId"),
                Instant.parse(json.getString("validUntil")),
                policy = policy,
                selectedDeviceIds = devices,
            )
            else -> error("unsupported persisted alarm state")
        }
    }

    private fun AlarmCampaignState.storageName(): String = when (this) {
        is AlarmCampaignState.Scheduled -> "scheduled"
        is AlarmCampaignState.Ringing -> "ringing"
        is AlarmCampaignState.Quiet -> "quiet"
        is AlarmCampaignState.Satisfied -> "satisfied"
        is AlarmCampaignState.Overridden -> "overridden"
    }

    private fun AlarmCampaignState.campaignStart(): Instant = when (this) {
        is AlarmCampaignState.Scheduled -> startsAt
        is AlarmCampaignState.Ringing -> ringStartedAt.minus(
            policy.ringDuration.plus(policy.quietDuration).multipliedBy((attempt - 1).toLong()),
        )
        is AlarmCampaignState.Quiet -> nextAttemptAt.minus(
            policy.ringDuration.plus(policy.quietDuration).multipliedBy(completedAttempt.toLong()),
        )
        is AlarmCampaignState.Satisfied -> satisfiedAt
        is AlarmCampaignState.Overridden -> overriddenAt
    }

    private fun bootId(): String = "boot-${Settings.Global.getInt(
        appContext.contentResolver,
        Settings.Global.BOOT_COUNT,
        0,
    )}"
}
