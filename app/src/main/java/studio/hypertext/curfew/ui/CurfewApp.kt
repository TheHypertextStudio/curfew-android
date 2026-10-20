package studio.hypertext.curfew.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    conditionVerified: Boolean = false,
    conditionBusy: Boolean = false,
    conditionMessage: String? = null,
    onExportCondition: () -> Unit = {},
    onVerifyCondition: () -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf("daily") }
    var taskParent by rememberSaveable { mutableStateOf("daily") }
    var saving by rememberSaveable { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()
    val canArm = readiness.canArm && callbackId != null && conditionLabel != null && conditionVerified

    fun open(task: String) {
        if (task == "edit" && destination != "edit") stateHolder.removeState("edit")
        taskParent = if (destination == "settings" || destination == "account") "settings" else "daily"
        destination = task
    }
    fun back() {
        if (destination == "edit") stateHolder.removeState("edit")
        destination = if (destination == "settings" || destination == "edit") "daily" else taskParent
    }
    BackHandler(destination != "daily") { back() }
    LaunchedEffect(accountRecoveryState) {
        if (accountRecoveryState is AccountRecoveryState.EnterRecoveryKey || accountRecoveryState is AccountRecoveryState.SaveRecoveryKey) {
            taskParent = "settings"
            destination = "account"
        }
    }
    LaunchedEffect(alarmBusy, alarmError, saving) {
        // A save can finish before Compose draws its intermediate busy state.
        if (saving && !alarmBusy) {
            saving = false
            if (alarmError == null) {
                stateHolder.removeState("edit")
                destination = "daily"
            }
        }
    }
    val title = when (destination) {
        "edit" -> "Edit alarm"
        "readiness" -> "Alarm access and sound"
        "check-in" -> "Check-in"
        "settings" -> "Settings"
        "account" -> "Account"
        else -> "Curfew"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (destination == "daily") Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CurfewHorizonMark()
                        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    } else Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                },
                navigationIcon = {
                    if (destination != "daily") IconButton(onClick = { back() }) {
                        Icon(painterResource(R.drawable.ic_arrow_back_rounded), "Back")
                    }
                },
                actions = {
                    if (destination == "daily") IconButton(onClick = { open("settings") }) {
                        Icon(painterResource(R.drawable.ic_settings_rounded), "Settings")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).semantics { paneTitle = if (destination == "daily") "Alarm" else title }) {
            val expanded = CurfewLayoutMode.forWindow(maxWidth.value.toInt(), maxHeight.value.toInt(), LocalDensity.current.fontScale) == CurfewLayoutMode.EXPANDED
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                if (expanded) {
                    Column(Modifier.width(248.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("daily" to "Alarm", "check-in" to "Check-in", "readiness" to "Alarm access and sound", "account" to "Account").forEach { (route, label) ->
                            NavigationDrawerItem(
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, selected = destination == route,
                                onClick = { open(route) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = if (destination == "daily") 420.dp else 640.dp).fillMaxSize()) {
                        stateHolder.SaveableStateProvider(destination) {
                            when (destination) {
                                "edit" -> SunriseScheduleEditor(
                                    sunriseAlarm, callbackId, canArm, alarmBusy || saving, alarmError,
                                    onSave = {
                                        saving = true
                                        onSaveAlarm(it)
                                    },
                                    onFinishSetup = { taskParent = "edit"; destination = if (readiness.canArm) "check-in" else "readiness" },
                                )
                                "readiness" -> AlarmReadinessScreen(
                                    readiness, onGrantExactAlarm, onGrantNotifications, onRepairAlarmChannel,
                                    onPlayTestSound, onAcknowledgeLimitations, onRaiseAlarmVolume,
                                    onDone = { if (!conditionVerified) destination = "check-in" else back() },
                                    onGrantFullScreen = onGrantFullScreen,
                                )
                                "check-in" -> CheckInScreen(
                                    callbackId, conditionLabel, conditionVerified, conditionBusy, conditionMessage,
                                    onSaveCallback, onExportCondition, onVerifyCondition, onDone = { back() },
                                )
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
                                    onEdit = { open("edit") }, onCheckIn = { open("check-in") },
                                    onSetup = { open(if (readiness.canArm) "check-in" else "readiness") },
                                    onToggle = onToggleAlarm,
                                )
                            }
                        }
                    }
                }
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
