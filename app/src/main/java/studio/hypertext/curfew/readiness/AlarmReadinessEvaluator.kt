package studio.hypertext.curfew.readiness

data class AlarmCapabilities(
    val canScheduleExactAlarms: Boolean,
    val notificationsGranted: Boolean,
    val fullScreenIntentAllowed: Boolean,
    val alarmChannelAudible: Boolean,
    val testSoundCompleted: Boolean,
    val limitationsAcknowledged: Boolean,
)

enum class ReadinessAction {
    GRANT_EXACT_ALARM_ACCESS,
    GRANT_NOTIFICATIONS,
    REPAIR_ALARM_CHANNEL,
    PLAY_TEST_SOUND,
    ACKNOWLEDGE_LIMITATIONS,
}

enum class ReadinessDisclosure {
    FULL_SCREEN_FALLBACK,
    DO_NOT_DISTURB_BEHAVIOR,
    BATTERY_AND_OEM_LIMITATIONS,
    OS_POWER_CONTROLS,
}

enum class AlarmPresentation {
    FULL_SCREEN,
    LOCK_SCREEN_NOTIFICATION,
}

data class AlarmReadiness(
    val canArm: Boolean,
    val requiredActions: Set<ReadinessAction>,
    val disclosures: Set<ReadinessDisclosure>,
    val presentation: AlarmPresentation,
    val mayChangeSystemVolume: Boolean = false,
)

class AlarmReadinessEvaluator {
    fun evaluate(capabilities: AlarmCapabilities): AlarmReadiness {
        val requiredActions = buildSet {
            if (!capabilities.canScheduleExactAlarms) {
                add(ReadinessAction.GRANT_EXACT_ALARM_ACCESS)
            }
            if (!capabilities.notificationsGranted) {
                add(ReadinessAction.GRANT_NOTIFICATIONS)
            }
            if (!capabilities.alarmChannelAudible) {
                add(ReadinessAction.REPAIR_ALARM_CHANNEL)
            }
            if (!capabilities.testSoundCompleted) {
                add(ReadinessAction.PLAY_TEST_SOUND)
            }
            if (!capabilities.limitationsAcknowledged) {
                add(ReadinessAction.ACKNOWLEDGE_LIMITATIONS)
            }
        }
        val disclosures = buildSet {
            add(ReadinessDisclosure.DO_NOT_DISTURB_BEHAVIOR)
            add(ReadinessDisclosure.BATTERY_AND_OEM_LIMITATIONS)
            add(ReadinessDisclosure.OS_POWER_CONTROLS)
            if (!capabilities.fullScreenIntentAllowed) {
                add(ReadinessDisclosure.FULL_SCREEN_FALLBACK)
            }
        }
        return AlarmReadiness(
            canArm = requiredActions.isEmpty(),
            requiredActions = requiredActions,
            disclosures = disclosures,
            presentation = if (capabilities.fullScreenIntentAllowed) {
                AlarmPresentation.FULL_SCREEN
            } else {
                AlarmPresentation.LOCK_SCREEN_NOTIFICATION
            },
        )
    }
}
