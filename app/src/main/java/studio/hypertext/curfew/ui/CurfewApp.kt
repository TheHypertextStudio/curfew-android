package studio.hypertext.curfew.ui

import studio.hypertext.curfew.alarm.AlarmSetupDraft

import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.R
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.readiness.AlarmReadiness

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CurfewApp(
    readiness: AlarmReadiness,
    accountEnrolled: Boolean,
    accountSignedIn: Boolean,
    accountRecoveryState: AccountRecoveryState?,
    onGrantExactAlarm: () -> Unit,
    onGrantNotifications: () -> Unit,
    onRepairAlarmChannel: () -> Unit,
    onPlayTestSound: () -> Unit,
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
) {
    val onGrantExactAlarmState = rememberUpdatedState(onGrantExactAlarm)
    val onGrantNotificationsState = rememberUpdatedState(onGrantNotifications)
    val onRepairAlarmChannelState = rememberUpdatedState(onRepairAlarmChannel)
    val onPlayTestSoundState = rememberUpdatedState(onPlayTestSound)
    val onAcknowledgeLimitationsState = rememberUpdatedState(onAcknowledgeLimitations)
    val onOpenAccountState = rememberUpdatedState(onOpenAccount)
    val onEnrollAccountState = rememberUpdatedState(onEnrollAccount)
    val onRestoreRecoveryKeyState = rememberUpdatedState(onRestoreRecoveryKey)
    val onAcknowledgeRecoveryKeyState = rememberUpdatedState(onAcknowledgeRecoveryKey)
    val onSaveCallbackState = rememberUpdatedState(onSaveCallback)
    val onSaveAlarmState = rememberUpdatedState(onSaveAlarm)
    val onToggleAlarmState = rememberUpdatedState(onToggleAlarm)
    val onRaiseAlarmVolumeState = rememberUpdatedState(onRaiseAlarmVolume)
    val onGrantFullScreenState = rememberUpdatedState(onGrantFullScreen)
    val onExportConditionState = rememberUpdatedState(onExportCondition)
    val onVerifyConditionState = rememberUpdatedState(onVerifyCondition)
    val onWelcomeChoiceState = rememberUpdatedState(onWelcomeChoice)
    val onPrepareDraftState = rememberUpdatedState(onPrepareDraft)
    val readinessState = rememberUpdatedState(readiness)
    val callbackState = rememberUpdatedState(callbackId)
    val labelState = rememberUpdatedState(conditionLabel)
    val alarmLabelState = rememberUpdatedState(alarmConditionLabel)
    val verifiedState = rememberUpdatedState(conditionVerified)
    val alarmState = rememberUpdatedState(sunriseAlarm)
    val busyState = rememberUpdatedState(alarmBusy)
    val errorState = rememberUpdatedState(alarmError)
    val conditionBusyState = rememberUpdatedState(conditionBusy)
    val conditionMessageState = rememberUpdatedState(conditionMessage)
    val recoveryState = rememberUpdatedState(accountRecoveryState)
    val enrolledState = rememberUpdatedState(accountEnrolled)
    val signedInState = rememberUpdatedState(accountSignedIn)
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var setupMode by rememberSaveable { mutableStateOf(startSetup || showWelcome) }
    var draft by rememberSaveable(stateSaver = AlarmSetupDraftSaver) { mutableStateOf(setupDraft) }
    val checkInState = rememberCheckInState()
    var setupError by remember { mutableStateOf<String?>(null) }
    val initial = when {
        showWelcome -> "welcome"
        startSetup && setupDraft != null -> if (!conditionVerified || callbackId == null || conditionLabel == null) "check-in" else if (!readiness.canArm) "readiness" else "review"
        startSetup -> "edit"
        else -> "daily"
    }
    val backStack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() }, restore = { it.toMutableStateList() },
    )) { mutableStateListOf("daily").apply {
        if (startSetup && setupDraft != null && initial != "edit") add("edit")
        if (initial != "daily") add(initial)
    } }
    val destination = backStack.last()
    var saving by remember { mutableStateOf(false) }

    fun open(task: String) {
        val existing = backStack.indexOf(task)
        if (existing >= 0) {
            while (backStack.lastIndex > existing) backStack.removeLastOrNull()
        } else backStack.add(task)
    }
    fun back() {
        if (preparing || saving || busyState.value) return
        if (backStack.last() == "check-in" && checkInState.editing) {
            checkInState.editing = false
            return
        }
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    fun daily() {
        backStack.clear()
        backStack.add("daily")
    }
    fun nextSetupTask() {
        open(when {
            draft == null -> "edit"
            (!verifiedState.value || callbackState.value == null || labelState.value == null) -> "check-in"
            !readinessState.value.canArm -> "readiness"
            else -> "review"
        })
    }
    LaunchedEffect(accountRecoveryState) {
        if (accountRecoveryState is AccountRecoveryState.EnterRecoveryKey || accountRecoveryState is AccountRecoveryState.SaveRecoveryKey) {
            open("account")
        }
    }
    LaunchedEffect(alarmBusy, alarmError, saving, sunriseAlarm, setupMode, draft?.id) {
        val activated = setupMode && sunriseAlarm?.schedule?.let { it.id == draft?.id && it.enabled } == true
        if (!alarmBusy && activated) {
            saving = false
            onSetupActivated()
            setupMode = false
            draft = null
            daily()
        } else if (saving && !alarmBusy) {
            if (alarmError != null) saving = false
            else if (!setupMode) {
                saving = false
                draft = null
                daily()
            }
        }
    }
    fun chooseWelcome(start: Boolean) {
        if (preparing) return
        preparing = true
        setupError = null
        scope.launch {
            try {
                onWelcomeChoiceState.value(start)
                setupMode = start
                if (start) open("edit") else daily()
            } catch (_: java.io.IOException) {
                setupError = "Couldn’t save setup. Try again."
            } finally { preparing = false }
        }
    }
    val title = when (destination) {
        "edit" -> "Wake time"
        "readiness" -> "Alarm setup"
        "check-in" -> "Check-in"
        "settings" -> "Settings"
        "account" -> "Account"
        "review" -> "Your alarm"
        else -> "Curfew"
    }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.getBottom(density)
    val availableHeight = with(density) { (LocalWindowInfo.current.containerSize.height - imeHeight).toDp() }
    val compactKeyboard = destination == "check-in" && imeHeight > 0 && availableHeight < 200.dp
    val taskTopBar: @Composable () -> Unit = {
            if (destination != "welcome") TopAppBar(
                title = {
                    if (destination == "daily") Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CurfewHorizonMark()
                        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                },
                navigationIcon = {
                    if (destination != "daily") IconButton(onClick = { back() }) {
                        Icon(painterResource(R.drawable.ic_arrow_back_rounded), "Back")
                    }
                },
                actions = {
                    if (destination == "daily") TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = {
                            PlainTooltip(Modifier.semantics {
                                // Explicit semantics work around Material tooltip announcement bug b/496338253.
                                liveRegion = LiveRegionMode.Assertive
                                paneTitle = "Settings"
                            }) { Text("Settings") }
                        },
                        state = rememberTooltipState(),
                    ) {
                        FilledTonalIconButton(
                            onClick = { setupMode = false; open("settings") },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_settings_rounded), "Settings")
                        }
                    }
                },
            )
    }
    val compactKeyboardState = rememberUpdatedState(compactKeyboard)
    val taskTopBarState = rememberUpdatedState(taskTopBar)
    Scaffold(
        contentWindowInsets = if (destination == "welcome") WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) else ScaffoldDefaults.contentWindowInsets,
        topBar = { if (!compactKeyboard) taskTopBar() },
    ) { padding ->
        SharedTransitionLayout {
            val sharedScope = this
            NavDisplay(
                backStack = backStack,
                transitionSpec = {
                    (slideInHorizontally(spatial) { it / 8 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects))
                },
                popTransitionSpec = {
                    (slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { it / 8 } + fadeOut(effects))
                },
                onBack = { back() },
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                entryProvider = { route ->
                    NavEntry(route) {
                        val onGrantExactAlarm by onGrantExactAlarmState
                        val onGrantNotifications by onGrantNotificationsState
                        val onRepairAlarmChannel by onRepairAlarmChannelState
                        val onPlayTestSound by onPlayTestSoundState
                        val onAcknowledgeLimitations by onAcknowledgeLimitationsState
                        val onOpenAccount by onOpenAccountState
                        val onEnrollAccount by onEnrollAccountState
                        val onRestoreRecoveryKey by onRestoreRecoveryKeyState
                        val onAcknowledgeRecoveryKey by onAcknowledgeRecoveryKeyState
                        val onSaveCallback by onSaveCallbackState
                        val onSaveAlarm by onSaveAlarmState
                        val onToggleAlarm by onToggleAlarmState
                        val onRaiseAlarmVolume by onRaiseAlarmVolumeState
                        val onGrantFullScreen by onGrantFullScreenState
                        val onExportCondition by onExportConditionState
                        val onVerifyCondition by onVerifyConditionState
                        val onWelcomeChoice by onWelcomeChoiceState
                        val onPrepareDraft by onPrepareDraftState
                        val readiness by readinessState
                        val callbackId by callbackState
                        val conditionLabel by labelState
                        val conditionVerified by verifiedState
                        val sunriseAlarm by alarmState
                        val alarmBusy by busyState
                        val alarmError by errorState
                        val conditionBusy by conditionBusyState
                        val conditionMessage by conditionMessageState
                        val accountRecoveryState by recoveryState
                        val accountEnrolled by enrolledState
                        val accountSignedIn by signedInState
                        val canArm = readiness.canArm && callbackId != null && conditionLabel != null && conditionVerified
                        CompositionLocalProvider(LocalCurfewMotion provides CurfewMotionScope(sharedScope, LocalNavAnimatedContentScope.current)) {
                            Box(Modifier.fillMaxSize().semantics { paneTitle = when (route) { "daily" -> "Alarm"; "readiness" -> "Alarm setup"; "edit" -> "Wake time"; "check-in" -> "Check-in"; "review" -> "Your alarm"; "settings" -> "Settings"; "account" -> "Account"; else -> "Welcome to Curfew" } }, contentAlignment = Alignment.TopCenter) {
                                Box(if (route == "welcome") Modifier.fillMaxSize() else if (route == "daily") Modifier.widthIn(max = 420.dp).fillMaxSize() else Modifier.fillMaxSize()) {
                                    when (route) {
                                        "welcome" -> CurfewWelcomeScreen(
                                            onContinue = { chooseWelcome(true) },
                                            onNotNow = { chooseWelcome(false) },
                                            busy = preparing, error = setupError,
                                        )
                                        "edit" -> SunriseScheduleEditor(
                                            sunriseAlarm, callbackId, canArm, alarmBusy || saving || preparing, alarmError ?: setupError,
                                            onSave = { saving = true; onSaveAlarm(it) },
                                            onFinishSetup = { open(if (readiness.canArm) "check-in" else "readiness") },
                                            setupMode = setupMode && sunriseAlarm == null,
                                            initialDraft = draft,
                                            onPrepare = { prepared -> scope.launch {
                                                preparing = true
                                                setupError = null
                                                try {
                                                    onPrepareDraft(prepared)
                                                    draft = prepared
                                                    nextSetupTask()
                                                } catch (_: java.io.IOException) {
                                                    setupError = "Couldn’t save setup. Try again."
                                                } finally { preparing = false }
                                            } },
                                        )
                                        "readiness" -> AlarmReadinessScreen(
                                            readiness, onGrantExactAlarm, onGrantNotifications, onRepairAlarmChannel,
                                            onPlayTestSound, onAcknowledgeLimitations, onRaiseAlarmVolume,
                                            onDone = {
                                                if (setupMode) nextSetupTask()
                                                else back()
                                            },
                                            onGrantFullScreen = onGrantFullScreen,
                                            inSetup = setupMode,
                                        )
                                        "check-in" -> CheckInScreen(
                                            callbackId, conditionLabel, conditionVerified, conditionBusy, conditionMessage,
                                            onSaveCallback, onExportCondition, onVerifyCondition,
                                            onDone = { if (setupMode) nextSetupTask() else back() },
                                            inSetup = setupMode, state = checkInState,
                                            inlineHeader = if (compactKeyboardState.value) taskTopBarState.value else null,
                                            onDefer = { chooseWelcome(false) },
                                            onUseForAlarm = if (sunriseAlarm != null && sunriseAlarm?.schedule?.callbackId != callbackId && conditionVerified) ({ open("edit") }) else null,
                                        )
                                        "review" -> draft?.let { prepared ->
                                            AlarmSetupReview(prepared, conditionLabel ?: "Check-in", alarmBusy || saving, alarmError ?: setupError, onEdit = { open("edit") }) {
                                                if (!canArm) nextSetupTask()
                                                else {
                                                    val schedule = prepared.toSchedule(requireNotNull(callbackId))
                                                    if (schedule.nextOccurrence(java.time.Instant.now(), java.time.ZoneId.systemDefault()) == null) {
                                                        setupError = "Choose a future wake time."
                                                    } else {
                                                        setupError = null
                                                        saving = true
                                                        onSaveAlarm(schedule)
                                                    }
                                                }
                                            }
                                        }
                                        "settings" -> CurfewSettingsScreen(
                                            conditionLabel, conditionVerified, readiness.canArm, accountSignedIn,
                                            onCheckIn = { open("check-in") }, onReadiness = { open("readiness") }, onAccount = { open("account") },
                                        )
                                        "account" -> CurfewAccountScreen(
                                            accountEnrolled, accountSignedIn, accountRecoveryState, onOpenAccount,
                                            onEnrollAccount, onRestoreRecoveryKey, onAcknowledgeRecoveryKey,
                                        )
                                        else -> SunriseDailyScreen(
                                            sunriseAlarm, if (sunriseAlarm != null) alarmLabelState.value else conditionLabel,
                                            conditionVerified && sunriseAlarm?.schedule?.callbackId == callbackId,
                                            canArm && (sunriseAlarm == null || sunriseAlarm?.schedule?.callbackId == callbackId), alarmBusy, alarmError,
                                            onEdit = { setupMode = sunriseAlarm == null; open("edit") },
                                            onCheckIn = { open("check-in") },
                                            onSetup = {
                                                setupMode = sunriseAlarm == null
                                                if (setupMode) nextSetupTask() else open(if (sunriseAlarm?.schedule?.callbackId != callbackId) "edit" else if (readiness.canArm) "check-in" else "readiness")
                                            },
                                            onToggle = onToggleAlarm,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}
