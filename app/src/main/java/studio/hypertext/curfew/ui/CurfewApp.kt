package studio.hypertext.curfew.ui

import studio.hypertext.curfew.alarm.AlarmSetupDraft

import kotlinx.coroutines.launch
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.semantics.contentDescription
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onSaveCallback: (String, String, String?) -> Unit,
    sunriseAlarm: SavedSunriseAlarm?,
    callbackId: String?,
    conditionLabel: String?,
    alarmBusy: Boolean,
    alarmError: String?,
    onSaveAlarm: (SunriseSchedule) -> Unit,
    onToggleAlarm: (Boolean) -> Unit,
    onRaiseAlarmVolume: () -> Unit = onRepairAlarmChannel,
    onGrantFullScreen: () -> Unit = {},
    showWelcome: Boolean = false,
    startSetup: Boolean = false,
    setupDraft: AlarmSetupDraft? = null,
    onWelcomeChoice: (Boolean) -> Unit = {},
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
    var draft by remember { mutableStateOf(setupDraft) }
    var setupError by remember { mutableStateOf<String?>(null) }
    val initial = when {
        showWelcome -> "welcome"
        startSetup && setupDraft != null -> if (!conditionVerified) "check-in" else if (!readiness.canArm) "readiness" else "review"
        startSetup -> "edit"
        else -> "daily"
    }
    val backStack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() }, restore = { it.toMutableStateList() },
    )) { mutableStateListOf("daily").apply { if (initial != "daily") add(initial) } }
    val destination = backStack.last()
    var saving by rememberSaveable { mutableStateOf(false) }

    fun open(task: String) {
        val existing = backStack.indexOf(task)
        if (existing >= 0) {
            while (backStack.lastIndex > existing) backStack.removeLastOrNull()
        } else backStack.add(task)
    }
    fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    fun daily() {
        backStack.clear()
        backStack.add("daily")
    }
    fun nextSetupTask() {
        open(when {
            draft == null -> "edit"
            !verifiedState.value -> "check-in"
            !readinessState.value.canArm -> "readiness"
            else -> "review"
        })
    }
    LaunchedEffect(destination, readiness.canArm, setupMode) {
        if (setupMode && destination == "readiness" && readiness.canArm) nextSetupTask()
    }
    LaunchedEffect(accountRecoveryState) {
        if (accountRecoveryState is AccountRecoveryState.EnterRecoveryKey || accountRecoveryState is AccountRecoveryState.SaveRecoveryKey) {
            open("account")
        }
    }
    LaunchedEffect(alarmBusy, alarmError, saving, sunriseAlarm) {
        if (saving && !alarmBusy) {
            if (alarmError != null) saving = false
            else if (!setupMode || sunriseAlarm?.schedule?.let { it.id == draft?.id && it.enabled } == true) {
                saving = false
                if (setupMode) onSetupActivated()
                setupMode = false
                draft = null
                daily()
            }
        }
    }
    val title = when (destination) {
        "edit" -> "Wake time"
        "readiness" -> ""
        "check-in" -> "Check-in"
        "settings" -> "Settings"
        "account" -> "Account"
        "review" -> "Your alarm"
        else -> "Curfew"
    }
    val rootDestination = destination == "daily" || destination == "settings"
    val adaptiveType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    NavigationSuiteScaffold(
        navigationSuiteType = if (rootDestination) adaptiveType else NavigationSuiteType.None,
        navigationItems = {
            listOf("daily" to "Alarm", "settings" to "Settings").forEach { (route, label) ->
                NavigationSuiteItem(
                    selected = destination == route,
                    onClick = { setupMode = false; daily(); if (route != "daily") open(route) },
                    icon = { Icon(painterResource(if (route == "daily") R.drawable.ic_alarm_rounded else R.drawable.ic_settings_rounded), null) },
                    label = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = if (route == "daily") "Alarm navigation" else "Settings" },
                )
            }
        },
    ) {
        Scaffold(
            contentWindowInsets = if (destination == "welcome") WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (destination != "welcome") TopAppBar(
                    title = {
                        if (destination == "daily") Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CurfewHorizonMark()
                            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    },
                    navigationIcon = {
                        if (!rootDestination) IconButton(onClick = { back() }) {
                            Icon(painterResource(R.drawable.ic_arrow_back_rounded), "Back")
                        }
                    },
                )
            },
        ) { padding ->
            SharedTransitionLayout {
                val sharedScope = this
                NavDisplay(
                    backStack = backStack,
                    onBack = { back() },
                    modifier = Modifier.fillMaxSize().padding(padding),
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
                                    Box(if (route == "welcome") Modifier.fillMaxSize() else Modifier.widthIn(max = if (route == "daily") 420.dp else 640.dp).fillMaxSize()) {
                                        when (route) {
                                            "welcome" -> CurfewWelcomeScreen(
                                                onContinue = { onWelcomeChoice(true); setupMode = true; open("edit") },
                                                onNotNow = { onWelcomeChoice(false); setupMode = false; daily() },
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
                                                    else if (!conditionVerified) open("check-in") else back()
                                                },
                                                onGrantFullScreen = onGrantFullScreen,
                                            )
                                            "check-in" -> CheckInScreen(
                                                callbackId, conditionLabel, conditionVerified, conditionBusy, conditionMessage,
                                                onSaveCallback, onExportCondition, onVerifyCondition,
                                                onDone = { if (setupMode) nextSetupTask() else back() },
                                                inSetup = setupMode,
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
                                                sunriseAlarm, conditionLabel, conditionVerified, canArm, alarmBusy, alarmError,
                                                onEdit = { setupMode = sunriseAlarm == null; open("edit") },
                                                onCheckIn = { open("check-in") },
                                                onSetup = {
                                                    setupMode = sunriseAlarm == null
                                                    if (setupMode) nextSetupTask() else open(if (readiness.canArm) "check-in" else "readiness")
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
}

@Composable
internal fun CurfewCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
