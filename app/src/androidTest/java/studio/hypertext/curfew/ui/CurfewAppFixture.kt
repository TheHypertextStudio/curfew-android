package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import studio.hypertext.curfew.account.AccountUiState
import studio.hypertext.curfew.alarm.AlarmSetupEntry
import studio.hypertext.curfew.alarm.AlarmSetupUiState
import studio.hypertext.curfew.enforcement.BedtimeProtectionUiState
import studio.hypertext.curfew.enforcement.EnforcementSessionState
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.enforcement.EnforcementPurpose
import studio.hypertext.curfew.enforcement.EnforcementSession
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.AlarmSoundCheckState

/**
 * Builds deterministic feature state for instrumented navigation and capture fixtures.
 */
@Composable
internal fun CurfewAppFixture(
    readiness: AlarmReadiness,
    accountEnrolled: Boolean,
    accountSignedIn: Boolean,
    accountRecoveryState: AccountRecoveryState?,
    onGrantExactAlarm: () -> Unit,
    onGrantNotifications: () -> Unit,
    onRepairAlarmChannel: () -> Unit,
    onPlayTestSound: () -> Unit,
    soundCheckState: AlarmSoundCheckState = AlarmSoundCheckState.CLOSED,
    onConfirmSound: () -> Unit = {},
    onCancelSound: () -> Unit = {},
    onAcknowledgeLimitations: () -> Unit,
    onOpenAccount: () -> Unit,
    onEnrollAccount: () -> Unit,
    onRestoreRecoveryKey: (String) -> Unit,
    onAcknowledgeRecoveryKey: () -> Unit,
    onSaveCallback: suspend (String, String, String?) -> String,
    sunriseAlarm: SavedSunriseAlarm?,
    callbackId: String?,
    conditionLabel: String?,
    alarmConditionLabel: String? = conditionLabel.takeIf { callbackId == sunriseAlarm?.schedule?.callbackId },
    alarmBusy: Boolean,
    alarmError: String?,
    onSaveAlarm: (SunriseSchedule) -> Unit,
    onToggleAlarm: (Boolean) -> Unit,
    onRaiseAlarmVolume: () -> Unit = onRepairAlarmChannel,
    onGrantFullScreen: () -> Unit = {},
    showWelcome: Boolean = false,
    startSetup: Boolean = false,
    setupDraft: AlarmSetupDraft? = null,
    onWelcomeChoice: suspend (Boolean) -> Unit = {},
    onPrepareDraft: suspend (AlarmSetupDraft) -> Unit = {},
    onSetupActivated: () -> Unit = {},
    conditionVerified: Boolean = false,
    conditionBusy: Boolean = false,
    conditionMessage: String? = null,
    onExportCondition: () -> Unit = {},
    onVerifyCondition: () -> Unit = {},
    conditionSetupFileSaved: Boolean = false,
    placeCodeSelected: Boolean = false,
    placeCheckInContent: (@Composable (() -> Unit, () -> Unit, (() -> Unit)?) -> Unit)? = null,
    enforcementSession: EnforcementSession? = null,
    enforcementChosenApps: Set<String> = emptySet(),
    isAccessibilityReady: Boolean = false,
    onToggleChosenApp: (String, Boolean) -> Unit = { _, _ -> },
    onOpenAccessibilitySettings: () -> Unit = {},
    onStartEnforcementSession: (EnforcementPurpose, Int) -> Unit = { _, _ -> },
    onEmergencyStopEnforcement: () -> Unit = {},
    interceptedPackage: String? = null,
    onDismissInterceptionBanner: () -> Unit = {},
) {
    val alarm = AlarmSetupUiState(
        loaded = true,
        readiness = readiness,
        sunriseAlarm = sunriseAlarm,
        callbackId = callbackId,
        conditionLabel = conditionLabel,
        alarmConditionLabel = alarmConditionLabel,
        conditionVerified = conditionVerified,
        placeCodeSelected = placeCodeSelected,
        alarmBusy = alarmBusy,
        alarmError = alarmError,
    )
    val alarmFlow = remember { MutableStateFlow(alarm) }
    SideEffect { alarmFlow.value = alarm }
    CurfewJourney(
        entry = AlarmSetupEntry(loaded = true, showWelcome = showWelcome, startSetup = startSetup, draft = setupDraft),
        initialAlarm = alarm,
        alarm = alarmFlow,
        actions = AlarmSetupActions(onSaveAlarm, onToggleAlarm, onWelcomeChoice, onPrepareDraft, onSetupActivated),
        navigationEffects = { navigation ->
            LaunchedEffect(accountRecoveryState) {
                if (accountRecoveryState != null && accountRecoveryState != AccountRecoveryState.Ready) {
                    navigation.navigate(CurfewDestination.Account)
                }
            }
            LaunchedEffect(interceptedPackage) {
                if (interceptedPackage != null) navigation.navigate(CurfewDestination.Enforcement)
            }
        },
    ) { destination, journey, inlineHeader ->
        when (destination) {
            CurfewDestination.CheckIn -> CheckInRoute(
                journey = journey,
                alarm = alarm,
                busy = conditionBusy,
                message = conditionMessage,
                fileSaved = conditionSetupFileSaved,
                onSave = onSaveCallback,
                onExport = onExportCondition,
                onVerify = onVerifyCondition,
                onWelcomeChoice = onWelcomeChoice,
                inlineHeader = inlineHeader,
                placeCheckInContent = placeCheckInContent,
            )
            CurfewDestination.Readiness -> ReadinessRoute(
                appState = journey,
                state = alarm,
                actions = ReadinessActions(
                    onGrantExactAlarm = onGrantExactAlarm,
                    onGrantNotifications = onGrantNotifications,
                    onRepairAlarmChannel = onRepairAlarmChannel,
                    onPlayTestSound = onPlayTestSound,
                    onAcknowledgeLimitations = onAcknowledgeLimitations,
                    onRaiseAlarmVolume = onRaiseAlarmVolume,
                    onGrantFullScreen = onGrantFullScreen,
                    onConfirmSound = onConfirmSound,
                    onCancelSound = onCancelSound,
                ),
                soundState = soundCheckState,
            )
            CurfewDestination.Settings -> SettingsRoute(
                journey,
                alarm,
                SettingsUiState(
                    accountSignedIn,
                    when {
                        enforcementSession?.state == EnforcementSessionState.ACTIVE -> ProtectionStatus.ACTIVE
                        isAccessibilityReady -> ProtectionStatus.READY
                        else -> ProtectionStatus.NEEDS_ATTENTION
                    },
                ),
            )
            CurfewDestination.Account -> AccountRoute(
                AccountUiState(accountEnrolled, accountSignedIn, accountRecoveryState),
                onOpenAccount,
                onEnrollAccount,
                onRestoreRecoveryKey,
                onAcknowledgeRecoveryKey,
            )
            CurfewDestination.Enforcement -> BedtimeRoute(
                BedtimeProtectionUiState(enforcementSession, enforcementChosenApps, isAccessibilityReady),
                onToggleChosenApp,
                onOpenAccessibilitySettings,
                onStartEnforcementSession,
                onEmergencyStopEnforcement,
                interceptedPackage,
                onDismissInterceptionBanner,
            )
            else -> Unit
        }
    }
}
