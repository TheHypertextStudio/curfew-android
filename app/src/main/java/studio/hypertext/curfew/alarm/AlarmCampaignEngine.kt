package studio.hypertext.curfew.alarm

import java.time.Duration
import java.time.Instant
import studio.hypertext.curfew.protocols.Result
import studio.hypertext.curfew.protocols.AlarmConfiguration

private val MAXIMUM_CAMPAIGN_DURATION: Duration = Duration.ofHours(2)
private val MINIMUM_RING_DURATION: Duration = Duration.ofSeconds(30)
private const val MAXIMUM_ATTEMPTS = 24

data class AlarmRecurrencePolicy(
    val maximumAttempts: Int,
    val ringDuration: Duration,
    val quietDuration: Duration,
) {
    init {
        require(maximumAttempts > 0) { "maximumAttempts must be positive" }
        require(maximumAttempts <= MAXIMUM_ATTEMPTS) {
            "maximumAttempts cannot exceed 24"
        }
        require(ringDuration >= MINIMUM_RING_DURATION) {
            "ringDuration must be at least 30 seconds"
        }
        require(!quietDuration.isNegative) { "quietDuration cannot be negative" }
        require(totalDuration <= MAXIMUM_CAMPAIGN_DURATION) {
            "alarm campaign cannot exceed two hours"
        }
    }

    val totalDuration: Duration
        get() = ringDuration.multipliedBy(maximumAttempts.toLong())
            .plus(quietDuration.multipliedBy((maximumAttempts - 1).toLong()))

    fun deadlineFrom(start: Instant): Instant = start.plus(totalDuration)

    companion object {
        fun default(): AlarmRecurrencePolicy = AlarmRecurrencePolicy(
            maximumAttempts = 3,
            ringDuration = Duration.ofMinutes(2),
            quietDuration = Duration.ofMinutes(5),
        )

        fun fromProtocol(configuration: AlarmConfiguration): AlarmRecurrencePolicy {
            val policy = AlarmRecurrencePolicy(
                maximumAttempts = configuration.maximumAttempts.toInt(),
                ringDuration = Duration.ofSeconds(configuration.ringDurationSeconds),
                quietDuration = Duration.ofSeconds(configuration.quietIntervalSeconds),
            )
            require(configuration.maximumAttempts == policy.maximumAttempts.toLong())
            require(configuration.campaignDurationSeconds == policy.totalDuration.seconds) {
                "protocol campaign duration must match the recurrence exactly"
            }
            require(configuration.selectedDeviceIds.isNotEmpty())
            return policy
        }
    }
}

sealed interface AlarmCampaignState {
    val campaignId: String
    val policy: AlarmRecurrencePolicy
    val selectedDeviceIds: Set<String>

    data class Scheduled(
        override val campaignId: String,
        val startsAt: Instant,
        override val policy: AlarmRecurrencePolicy,
        override val selectedDeviceIds: Set<String>,
    ) : AlarmCampaignState {
        init {
            require(campaignId.isNotBlank())
            require(selectedDeviceIds.isNotEmpty())
        }
    }

    data class Ringing(
        override val campaignId: String,
        val attempt: Int,
        val ringStartedAt: Instant,
        val ringEndsAt: Instant,
        override val policy: AlarmRecurrencePolicy,
        override val selectedDeviceIds: Set<String>,
    ) : AlarmCampaignState

    data class Quiet(
        override val campaignId: String,
        val completedAttempt: Int,
        val nextAttemptAt: Instant,
        override val policy: AlarmRecurrencePolicy,
        override val selectedDeviceIds: Set<String>,
    ) : AlarmCampaignState

    data class Satisfied(
        override val campaignId: String,
        val satisfiedAt: Instant,
        val conditionLabel: String,
        val outcome: Result = Result.Satisfied,
        val devicesToRelease: Set<String>,
        val releasesWakeGate: Boolean = true,
        override val policy: AlarmRecurrencePolicy,
        override val selectedDeviceIds: Set<String>,
    ) : AlarmCampaignState

    data class Exhausted(
        override val campaignId: String,
        val exhaustedAt: Instant,
        val outcome: Result = Result.Exhausted,
        val releasesWakeGate: Boolean = true,
        override val policy: AlarmRecurrencePolicy,
        override val selectedDeviceIds: Set<String>,
    ) : AlarmCampaignState

    data class Overridden(
        override val campaignId: String,
        val overriddenAt: Instant,
        val overrideId: String,
        val validUntil: Instant,
        val outcome: Result = Result.RemoteOverride,
        val releasesWakeGate: Boolean = true,
        override val policy: AlarmRecurrencePolicy,
        override val selectedDeviceIds: Set<String>,
    ) : AlarmCampaignState
}

class AlarmCampaignEngine {
    fun advance(state: AlarmCampaignState, now: Instant): AlarmCampaignState = when (state) {
        is AlarmCampaignState.Scheduled -> {
            if (now < state.startsAt) {
                state
            } else {
                ringing(state, attempt = 1, startsAt = state.startsAt)
            }
        }

        is AlarmCampaignState.Ringing -> {
            if (now < state.ringEndsAt) {
                state
            } else if (state.attempt >= state.policy.maximumAttempts) {
                AlarmCampaignState.Exhausted(
                    campaignId = state.campaignId,
                    exhaustedAt = state.ringEndsAt,
                    policy = state.policy,
                    selectedDeviceIds = state.selectedDeviceIds,
                )
            } else {
                AlarmCampaignState.Quiet(
                    campaignId = state.campaignId,
                    completedAttempt = state.attempt,
                    nextAttemptAt = state.ringEndsAt.plus(state.policy.quietDuration),
                    policy = state.policy,
                    selectedDeviceIds = state.selectedDeviceIds,
                )
            }
        }

        is AlarmCampaignState.Quiet -> {
            if (now < state.nextAttemptAt) {
                state
            } else {
                ringing(
                    state = state,
                    attempt = state.completedAttempt + 1,
                    startsAt = state.nextAttemptAt,
                )
            }
        }

        is AlarmCampaignState.Exhausted,
        is AlarmCampaignState.Overridden,
        is AlarmCampaignState.Satisfied,
        -> state
    }

    fun advanceTo(state: AlarmCampaignState, now: Instant): AlarmCampaignState {
        var current = state
        while (true) {
            val next = advance(current, now)
            if (next == current) return current
            current = next
        }
    }

    fun satisfy(
        state: AlarmCampaignState,
        at: Instant,
        conditionLabel: String,
    ): AlarmCampaignState {
        require(state.isPending()) { "campaign is already terminal" }
        require(conditionLabel.isNotBlank())
        validateObservationTime(state, at)
        return AlarmCampaignState.Satisfied(
            campaignId = state.campaignId,
            satisfiedAt = at,
            conditionLabel = conditionLabel,
            devicesToRelease = state.selectedDeviceIds,
            policy = state.policy,
            selectedDeviceIds = state.selectedDeviceIds,
        )
    }

    fun onConditionUnavailable(state: AlarmCampaignState): AlarmCampaignState = state

    fun override(
        state: AlarmCampaignState,
        at: Instant,
        overrideId: String,
        validUntil: Instant,
    ): AlarmCampaignState {
        require(state.isPending()) { "campaign is already terminal" }
        require(overrideId.isNotBlank())
        require(at <= validUntil) { "remote override is expired" }
        validateObservationTime(state, at)
        return AlarmCampaignState.Overridden(
            campaignId = state.campaignId,
            overriddenAt = at,
            overrideId = overrideId,
            validUntil = validUntil,
            policy = state.policy,
            selectedDeviceIds = state.selectedDeviceIds,
        )
    }

    private fun ringing(
        state: AlarmCampaignState,
        attempt: Int,
        startsAt: Instant,
    ) = AlarmCampaignState.Ringing(
        campaignId = state.campaignId,
        attempt = attempt,
        ringStartedAt = startsAt,
        ringEndsAt = startsAt.plus(state.policy.ringDuration),
        policy = state.policy,
        selectedDeviceIds = state.selectedDeviceIds,
    )

    private fun validateObservationTime(state: AlarmCampaignState, at: Instant) {
        val campaignStartsAt = state.campaignStartedAt()
        require(at >= campaignStartsAt) { "observation predates campaign" }
        require(at < state.policy.deadlineFrom(campaignStartsAt)) {
            "observation is at or after campaign deadline"
        }
    }
}

fun AlarmCampaignState.isPending(): Boolean =
    this is AlarmCampaignState.Scheduled ||
        this is AlarmCampaignState.Ringing ||
        this is AlarmCampaignState.Quiet

fun AlarmCampaignState.campaignStartedAt(): Instant = when (this) {
    is AlarmCampaignState.Scheduled -> startsAt
    is AlarmCampaignState.Ringing -> ringStartedAt.minus(
        policy.ringDuration.plus(policy.quietDuration)
            .multipliedBy((attempt - 1).toLong()),
    )
    is AlarmCampaignState.Quiet -> nextAttemptAt.minus(
        policy.ringDuration.plus(policy.quietDuration)
            .multipliedBy(completedAttempt.toLong()),
    )
    is AlarmCampaignState.Exhausted,
    is AlarmCampaignState.Overridden,
    is AlarmCampaignState.Satisfied,
    -> error("terminal campaigns do not accept observations")
}
