package studio.hypertext.curfew.ui

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import studio.hypertext.curfew.LaunchRequest
import studio.hypertext.curfew.account.AccountViewModel
import studio.hypertext.curfew.alarm.AlarmSetupViewModel
import studio.hypertext.curfew.alarm.AlarmSetupUiState
import studio.hypertext.curfew.platform.SystemSettingsLauncher
import studio.hypertext.curfew.ui.theme.CurfewTheme

private const val APP_SHELL_KEY = "app-shell"

@Composable
internal fun CurfewRoot(
    systemSettings: SystemSettingsLauncher,
    protectionEntry: StateFlow<LaunchRequest.Enforcement?>,
    onProtectionEntryConsumed: (LaunchRequest.Enforcement) -> Unit,
) {
    // These lifetimes cross navigation entries: alarm presentation, browser
    // sign-in, and the setup journey that spans several alarm destinations.
    val ringing: RingingAlarmViewModel = hiltViewModel()
    val account: AccountViewModel = hiltViewModel()
    val setup: AlarmSetupViewModel = hiltViewModel()
    val activeAlarm by ringing.presentation.collectAsStateWithLifecycle()
    val entry by setup.entry.collectAsStateWithLifecycle()
    val initialAlarm by produceState<AlarmSetupUiState?>(null, setup) {
        value = setup.state.first { it.loaded }
    }
    val shell = rememberSaveableStateHolder()

    RestoreOnResumeEffect(setup::onResume)
    AccountFeedback(account)
    LocalActivity.current?.let { ShowOverKeyguardEffect(it, active = activeAlarm != null) }

    CurfewTheme {
        val presentation = activeAlarm
        val initial = initialAlarm
        when {
            presentation != null -> key(presentation.snapshot.state.campaignId) {
                AlarmRingingHost(
                    alarm = presentation,
                    onApprovePlaceCode = ringing::approvePlaceCode,
                    onOpenCheckIn = systemSettings::openCheckIn,
                    onSecuritySettings = systemSettings::openSecuritySettings,
                )
            }
            !entry.loaded -> SetupLoading()
            entry.loadError -> FirstUseLoadErrorScreen(setup::retryFirstUse)
            initial == null -> SetupLoading()
            else -> shell.SaveableStateProvider(APP_SHELL_KEY) {
                CurfewJourney(
                    entry = entry,
                    initialAlarm = initial,
                    alarm = setup.state,
                    actions = AlarmSetupActions(
                        onSaveAlarm = setup::saveAlarm,
                        onToggleAlarm = setup::toggleAlarm,
                        onWelcomeChoice = setup::chooseWelcome,
                        onPrepareDraft = setup::prepareDraft,
                        onSetupActivated = setup::clearDraft,
                    ),
                    navigationEffects = { navigation ->
                        AccountRecoveryNavigation(account, navigation)
                        ProtectionNavigation(protectionEntry, navigation)
                    },
                ) { destination, journey, inlineHeader ->
                    when (destination) {
                        CurfewDestination.CheckIn -> CheckInEntry(
                            journey,
                            setup.state.collectAsStateWithLifecycle().value,
                            setup::chooseWelcome,
                            inlineHeader,
                        )
                        CurfewDestination.Readiness -> ReadinessEntry(
                            journey,
                            setup.state.collectAsStateWithLifecycle().value,
                            systemSettings,
                            setup::refreshCapabilities,
                            setup::acknowledgeLimitations,
                        )
                        CurfewDestination.Settings -> SettingsEntry(journey, setup.state.collectAsStateWithLifecycle().value)
                        CurfewDestination.Enforcement -> BedtimeEntry(systemSettings, protectionEntry, onProtectionEntryConsumed)
                        CurfewDestination.Account -> AccountEntry(account, systemSettings)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRecoveryNavigation(account: AccountViewModel, navigation: CurfewNavigationState) {
    val recovery by account.needsRecovery.collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(recovery) {
        if (recovery) navigation.navigate(CurfewDestination.Account)
    }
}

@Composable
private fun ProtectionNavigation(
    entries: StateFlow<LaunchRequest.Enforcement?>,
    navigation: CurfewNavigationState,
) {
    val entry by entries.collectAsStateWithLifecycle()
    LaunchedEffect(entry) {
        if (entry != null) navigation.navigate(CurfewDestination.Enforcement)
    }
}

@Composable
private fun AccountFeedback(account: AccountViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(account, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            account.messages.collect { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun SetupLoading() {
    Surface(Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
