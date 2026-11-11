package studio.hypertext.curfew.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persisted Sunrise schedule and its currently assigned occurrence. [completedThrough]
 * prevents a completed local date from being selected again.
 */
data class SavedSunriseAlarm(
    val schedule: SunriseSchedule,
    val occurrence: SunriseOccurrence?,
    val completedThrough: LocalDate? = null,
)

/**
 * Reads and writes the durable alarm journal. The schedule controller uses this port so its
 * decisions can be tested without DataStore.
 */
interface SunriseScheduleStore {
    suspend fun read(): SavedSunriseAlarm?
    suspend fun write(alarm: SavedSunriseAlarm)
}

/**
 * Represents the platform work required to arm, inspect, and cancel occurrences. The
 * controller separates this work from its planning and journal logic.
 */
interface SunriseScheduleRuntime {
    suspend fun canArm(callbackId: String): Boolean
    suspend fun phase(campaignId: String): SunriseOccurrencePhase?
    suspend fun arm(occurrence: SunriseOccurrence, callbackId: String)
    suspend fun cancel(campaignId: String)
}

/**
 * Raised when an enabled schedule has no future occurrence. Callers can show the date
 * correction without depending on exception text.
 */
internal class FutureAlarmTimeRequired : IllegalArgumentException()

/**
 * Coordinates schedule planning, runtime registration, and journal updates under one lock. A
 * save publishes a new schedule only after it is safe to arm the corresponding occurrence.
 */
class SunriseScheduleController(
    private val store: SunriseScheduleStore,
    private val runtime: SunriseScheduleRuntime,
    private val mutex: Mutex = Mutex(),
) {
    suspend fun save(schedule: SunriseSchedule, now: Instant, zone: ZoneId): SavedSunriseAlarm =
        mutex.withLock { apply(schedule, store.read(), now, zone, requireOccurrence = true) }

    suspend fun revokeCondition(
        callbackId: String,
        now: Instant,
        zone: ZoneId,
        revoke: suspend () -> Unit,
    ) = mutex.withLock {
        val previous = store.read()
        if (previous?.schedule?.callbackId == callbackId && previous.schedule.enabled) {
            apply(previous.schedule.copy(enabled = false), previous, now, zone, requireOccurrence = false)
        }
        revoke()
    }

    suspend fun restore(now: Instant, zone: ZoneId): SavedSunriseAlarm? = mutex.withLock {
        val previous = store.read() ?: return@withLock null
        apply(previous.schedule, previous, now, zone, requireOccurrence = false)
    }

    suspend fun consume(campaignId: String): SavedSunriseAlarm? = mutex.withLock {
        val previous = store.read() ?: return@withLock null
        val occurrence = previous.occurrence ?: return@withLock previous
        if (occurrence.campaignId != campaignId ||
            runtime.phase(campaignId) != SunriseOccurrencePhase.FINISHED
        ) return@withLock previous

        // Commit release independently of readiness for the next morning.
        val completed = previous.copy(
            occurrence = null,
            completedThrough = maxOf(previous.completedThrough ?: occurrence.localDate, occurrence.localDate),
        )
        store.write(completed)
        completed
    }

    private suspend fun apply(
        schedule: SunriseSchedule,
        previous: SavedSunriseAlarm?,
        now: Instant,
        zone: ZoneId,
        requireOccurrence: Boolean,
    ): SavedSunriseAlarm {
        val phase = previous?.occurrence?.let {
            // A committed occurrence with missing runtime must be reconstructed, not skipped.
            runtime.phase(it.campaignId) ?: SunriseOccurrencePhase.SCHEDULED
        }
        val sameAlarm = previous?.schedule?.id == schedule.id
        val previousOccurrence = previous?.occurrence?.takeUnless {
            !sameAlarm && phase == SunriseOccurrencePhase.FINISHED
        }
        val plan = SunriseSchedulePlanner().plan(
            schedule, now, zone, previousOccurrence, phase,
            previous?.completedThrough?.takeIf { sameAlarm },
        )
        val next = SavedSunriseAlarm(schedule, plan.occurrence, plan.completedThrough)
        if (requireOccurrence && schedule.enabled && plan.occurrence == null) throw FutureAlarmTimeRequired()
        val shouldArm = plan.occurrence != null && phase != SunriseOccurrencePhase.ACTIVE
        if (schedule.enabled && plan.occurrence != null &&
            (requireOccurrence || phase != SunriseOccurrencePhase.ACTIVE) &&
            !runtime.canArm(schedule.callbackId)
        ) {
            throw IllegalStateException("Finish alarm setup and connect your check-in.")
        }
        if (shouldArm) {
            runtime.arm(requireNotNull(plan.occurrence), schedule.callbackId)
        }
        try {
            store.write(next)
        } catch (failure: Exception) {
            if (shouldArm) {
                runCatching {
                    if (previous?.occurrence?.campaignId != plan.occurrence.campaignId) {
                        runtime.cancel(requireNotNull(plan.occurrence).campaignId)
                    }
                    if (previous?.occurrence != null && phase == SunriseOccurrencePhase.SCHEDULED) {
                        runtime.arm(previous.occurrence, previous.schedule.callbackId)
                    }
                }.onFailure(failure::addSuppressed)
            }
            throw failure
        }
        plan.cancelCampaignId?.let { runtime.cancel(it) }
        return next
    }
}
