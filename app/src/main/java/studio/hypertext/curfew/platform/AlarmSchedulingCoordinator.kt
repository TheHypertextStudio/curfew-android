package studio.hypertext.curfew.platform

import java.time.Instant
import studio.hypertext.curfew.readiness.AlarmReadiness

interface AlarmClockGateway {
    fun canScheduleExactAlarms(): Boolean
    fun setAlarmClock(campaignId: String, triggerAt: Instant)
    fun cancel(campaignId: String)
}

data class PendingAlarm(val campaignId: String, val triggerAt: Instant)

enum class RescheduleReason {
    BOOT_COMPLETED,
    PACKAGE_REPLACED,
    TIME_CHANGED,
    TIMEZONE_CHANGED,
    EXACT_ALARM_PERMISSION_GRANTED,
}

class AlarmNotReadyException(message: String) : IllegalStateException(message)

class AlarmSchedulingCoordinator(private val gateway: AlarmClockGateway) {
    fun arm(campaignId: String, triggerAt: Instant, readiness: AlarmReadiness) {
        if (!readiness.canArm) throw AlarmNotReadyException("alarm readiness is incomplete")
        if (!gateway.canScheduleExactAlarms()) {
            throw AlarmNotReadyException("exact alarm permission is unavailable")
        }
        gateway.setAlarmClock(campaignId, triggerAt)
    }

    fun restore(pending: List<PendingAlarm>, reason: RescheduleReason) {
        @Suppress("UNUSED_VARIABLE") val recoveryReason = reason
        if (!gateway.canScheduleExactAlarms()) return
        pending.forEach { gateway.setAlarmClock(it.campaignId, it.triggerAt) }
    }
}
