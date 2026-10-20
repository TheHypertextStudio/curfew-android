package studio.hypertext.curfew.platform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.alarm.AlarmRuntimeSnapshot
import studio.hypertext.curfew.alarm.AlarmRuntimeClock
import studio.hypertext.curfew.alarm.AlarmRecoveryPlanner
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.persistence.AlarmCampaignEntity
import studio.hypertext.curfew.persistence.CurfewDatabase

class AlarmRuntimeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = CurfewDatabase.open(appContext)
    private val dao = database.alarmCampaignDao()

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

    fun observeSnapshot(campaignId: String): Flow<AlarmRuntimeSnapshot?> =
        dao.observe(campaignId).map { it?.let(::snapshotOf) }.distinctUntilChanged()

    fun observe(campaignId: String): Flow<AlarmCampaignState?> =
        dao.observe(campaignId).map { it?.let(::decode) }.distinctUntilChanged()

    suspend fun save(
        state: AlarmCampaignState,
        writerCounter: Long? = null,
        callbackId: String? = null,
    ): AlarmCampaignState = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existing = dao.find(state.campaignId)
            val stored = existing?.let(::decode)
            // Late network and lifecycle writes must not undo release or replay an earlier interval.
            if (stored is AlarmCampaignState.Satisfied || stored is AlarmCampaignState.Overridden ||
                (stored != null && state.progress() < stored.progress())
            ) {
                if (writerCounter != null && writerCounter > requireNotNull(existing).writerCounter) {
                    dao.upsert(existing.copy(writerCounter = writerCounter))
                }
                return@withTransaction stored
            }
            val checkpoint = AlarmRuntimeClock.checkpoint(
                existing?.let { AlarmRuntimeSnapshot(requireNotNull(stored), Instant.ofEpochMilli(it.persistedAtWall), it.persistedAtElapsed, it.bootId) },
                state, Instant.now(), SystemClock.elapsedRealtime(), bootId(),
            )
            dao.upsert(
                AlarmCampaignEntity(
                    campaignId = state.campaignId,
                    protocolJson = encode(state).toString(),
                    state = state.storageName(),
                    // This legacy column has no release-deadline authority in persistent campaigns.
                    finalDeadlineAt = Long.MAX_VALUE,
                    // An active interval keeps its elapsed-time anchor across wall-clock changes.
                    persistedAtWall = checkpoint.persistedAtWall.toEpochMilli(),
                    persistedAtElapsed = checkpoint.persistedAtElapsedMillis,
                    bootId = checkpoint.bootId,
                    writerCounter = maxOf(writerCounter ?: 0, existing?.writerCounter ?: 0),
                    callbackId = callbackId ?: existing?.callbackId,
                ),
            )
            state
        }
    }

    suspend fun pending(): List<AlarmCampaignState> = withContext(Dispatchers.IO) {
        dao.pending().map(::decode)
    }

    suspend fun pendingSnapshots(): List<AlarmRuntimeSnapshot> = withContext(Dispatchers.IO) {
        dao.pending().map(::snapshotOf)
    }

    suspend fun snapshot(campaignId: String): AlarmRuntimeSnapshot? = withContext(Dispatchers.IO) {
        dao.find(campaignId)?.let(::snapshotOf)
    }

    private fun snapshotOf(entity: AlarmCampaignEntity) = AlarmRuntimeSnapshot(
        decode(entity), Instant.ofEpochMilli(entity.persistedAtWall), entity.persistedAtElapsed, entity.bootId,
    )

    suspend fun recover(campaignId: String): AlarmCampaignState? = snapshot(campaignId)?.let {
        save(AlarmRecoveryPlanner(AlarmCampaignEngine()).recover(
            it, Instant.now(), SystemClock.elapsedRealtime(), bootId(),
        ))
    }

    suspend fun nextTransitionAt(campaignId: String): Instant? = snapshot(campaignId)?.let {
        AlarmRuntimeClock.nextTransitionAt(it, Instant.now(), SystemClock.elapsedRealtime(), bootId())
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

    private fun AlarmCampaignState.progress(): Long = when (this) {
        is AlarmCampaignState.Scheduled -> 0L
        is AlarmCampaignState.Ringing -> attempt.toLong() * 2 - 1
        is AlarmCampaignState.Quiet -> completedAttempt.toLong() * 2
        is AlarmCampaignState.Satisfied, is AlarmCampaignState.Overridden -> Long.MAX_VALUE
    }

    private fun bootId(): String = "boot-${Settings.Global.getInt(
        appContext.contentResolver,
        Settings.Global.BOOT_COUNT,
        0,
    )}"
}
