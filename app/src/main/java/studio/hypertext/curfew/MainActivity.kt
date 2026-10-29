package studio.hypertext.curfew

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.account.AndroidAccountRecovery
import studio.hypertext.curfew.account.BrowserOAuthEnrollment
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.account.OAuthTokenExchange
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRuntimeClock
import studio.hypertext.curfew.alarm.AlarmRuntimeSnapshot
import studio.hypertext.curfew.alarm.OwnerRecoveryResult
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.alarm.campaignStartedAt
import studio.hypertext.curfew.callback.AndroidWakeConditionSetup
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.checkin.PlaceApprovalResult
import studio.hypertext.curfew.config.ProductionServices
import studio.hypertext.curfew.persistence.CurfewPreferenceState
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.persistence.FirstUseChoice
import studio.hypertext.curfew.persistence.FirstUseLoadState
import studio.hypertext.curfew.persistence.FirstUsePreferences
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway
import studio.hypertext.curfew.platform.AndroidAlarmSoundPreview
import studio.hypertext.curfew.platform.AndroidOwnerRecovery
import studio.hypertext.curfew.platform.PerpetualAlarmService
import studio.hypertext.curfew.platform.SunriseScheduler
import studio.hypertext.curfew.protocols.CallbackDefinition
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.AlarmSoundCheck
import studio.hypertext.curfew.sync.WakeSyncScheduler
import studio.hypertext.curfew.ui.AlarmRecoveryScreen
import studio.hypertext.curfew.ui.AlarmRingingScreen
import studio.hypertext.curfew.ui.AlarmSoundCheckDialog
import studio.hypertext.curfew.ui.CurfewApp
import studio.hypertext.curfew.ui.FirstUseLoadErrorScreen
import studio.hypertext.curfew.ui.alarmSaveError
import studio.hypertext.curfew.ui.theme.CurfewTheme

private data class PlaceScanRequest(
    val label: String,
    val draftId: String? = null,
    val codeId: String? = null,
    val campaignId: String? = null,
)

private data class SetupInputs(
    val preferences: CurfewPreferenceState,
    val selected: studio.hypertext.curfew.checkin.CheckInDescription?,
    val alarmConnection: studio.hypertext.curfew.checkin.CheckInDescription?,
)

class MainActivity : ComponentActivity() {
    private lateinit var preferences: CurfewPreferences
    private val soundPreview by lazy { AndroidAlarmSoundPreview(this) }
    private val soundCheck by lazy {
        AlarmSoundCheck(soundPreview::play, soundPreview::stop) { preferences.setTestSoundConfirmed(true) }
    }
    private var testSoundJob: Job? = null
    private var capabilityEpoch by mutableIntStateOf(0)
    private var accountRecoveryState by mutableStateOf<AccountRecoveryState?>(null)
    private val sunrise by lazy { SunriseScheduler(this) }
    private var sunriseSaving by mutableStateOf(false)
    private var sunriseError by mutableStateOf<String?>(null)
    private var sunriseReadFailed by mutableStateOf(false)
    private var requestedAlarmSnapshot by mutableStateOf<AlarmRuntimeSnapshot?>(null)
    private var requestedAlarmLoaded by mutableStateOf(false)
    private var recoveryVisible by mutableStateOf(false)
    private val ownerRecoveryDelegate =
        lazy {
            AndroidOwnerRecovery(activity = this, onRecovered = { result ->
                recoveryVisible = false
                val message =
                    when (result) {
                        OwnerRecoveryResult.Recovered -> "Alarm stopped through recovery."
                        OwnerRecoveryResult.RecoveredNextAlarmUnavailable -> "Alarm stopped. Check tomorrow’s alarm."
                        OwnerRecoveryResult.NotActive -> "This alarm is not ringing."
                        OwnerRecoveryResult.NotSaved -> "Couldn’t stop the alarm. Try again."
                    }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            })
        }
    private val ownerRecovery by ownerRecoveryDelegate
    private val sunriseState by lazy {
        sunrise.state.onEach { sunriseReadFailed = false }.catch {
            sunriseReadFailed = true
            emit(null)
        }
    }

    private val wakeConditionSetup by lazy { AndroidWakeConditionSetup(this) }
    private var conditionBusy by mutableStateOf(false)
    private var conditionMessage by mutableStateOf<String?>(null)
    private var pendingVerifierExportId: String? = null
    private var exportedVerifierId by mutableStateOf<String?>(null)
    private val exportVerifier =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val callbackId = pendingVerifierExportId
            pendingVerifierExportId = null
            if (uri == null || callbackId == null) {
                conditionMessage = "File not saved."
            } else {
                conditionBusy = true
                lifecycleScope.launch {
                    try {
                        val definition = wakeConditionSetup.provision(callbackId)
                        withContext(Dispatchers.IO) {
                            requireNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use {
                                it.write(Json.encodeToString(definition))
                            }
                        }
                        exportedVerifierId = callbackId
                        conditionMessage = "Setup file saved."
                    } catch (failure: Exception) {
                        conditionMessage = "Couldn’t save the setup file. Choose another location and try again."
                    } finally {
                        conditionBusy = false
                    }
                }
            }
        }

    private val places by lazy {
        studio.hypertext.curfew.checkin
            .PlaceCheckInRepository(this)
    }
    private var placeScanRequest by mutableStateOf<PlaceScanRequest?>(null)
    private var placeScanCommitting by mutableStateOf(false)
    private var placeEpoch by mutableIntStateOf(0)
    private var placeExportBusy by mutableStateOf(false)
    private var placeExportError by mutableStateOf<String?>(null)
    private var pendingPlaceExportId: String? = null
    private val exportPlace =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            val id = pendingPlaceExportId
            pendingPlaceExportId = null
            if (uri == null || id == null) {
                placeExportBusy = false
            } else {
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val draft = requireNotNull(places.draft())
                            require(draft.enrollment.id == id)
                            val bitmap =
                                studio.hypertext.curfew.checkin.PlaceCodeImage
                                    .create(draft.payload)
                            try {
                                requireNotNull(contentResolver.openOutputStream(uri)).use { output ->
                                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                                }
                            } finally {
                                bitmap.recycle()
                            }
                            places.markExported(id)
                        }
                        placeEpoch++
                    } catch (
                        cancelled: kotlinx.coroutines.CancellationException,
                    ) {
                        throw cancelled
                    } catch (
                        _: Exception,
                    ) {
                        placeExportError = "Couldn’t save the code. Choose another location and try again."
                    } finally {
                        placeExportBusy = false
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingPlaceExportId = savedInstanceState?.getString("pending_place_export")
        placeExportBusy = pendingPlaceExportId != null
        pendingVerifierExportId = savedInstanceState?.getString("pending_check_in_export")
        exportedVerifierId = savedInstanceState?.getString("saved_check_in_export")
        enableEdgeToEdge()
        PerpetualAlarmService.ensureChannels(this)
        WakeSyncScheduler.install(this)
        preferences = CurfewPreferences(this)
        val requestedId = intent.getStringExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID)
        if (intent.getBooleanExtra(EXTRA_SHOW_ALARM, false) && requestedId != null) {
            lifecycleScope.launch {
                requestedAlarmSnapshot =
                    runCatching {
                        AlarmRuntimeRepository(this@MainActivity).snapshot(requestedId)
                    }.getOrNull()
                requestedAlarmLoaded = true
                if (requestedAlarmSnapshot?.state is AlarmCampaignState.Ringing ||
                    requestedAlarmSnapshot?.state is AlarmCampaignState.Quiet
                ) {
                    presentOverKeyguard()
                }
            }
        } else {
            requestedAlarmLoaded = true
        }
        accountRecoveryState = AndroidAccountRecovery(this).pendingState()
        if (handleOAuthCallback(intent)) {
            setIntent(Intent(this, MainActivity::class.java))
        }
        setContent {
            @Suppress("UNUSED_EXPRESSION")
            capabilityEpoch
            var firstUseRetry by androidx.compose.runtime.saveable
                .rememberSaveable { mutableIntStateOf(0) }
            val savedAlarmResult =
                remember(firstUseRetry) { sunriseState.map { true to it } }
                    .collectAsStateWithLifecycle(initialValue = false to null)
                    .value
            val savedAlarm = savedAlarmResult.second
            val firstUsePreferences = remember { FirstUsePreferences(this@MainActivity) }
            val firstUseFlow = remember(firstUsePreferences, firstUseRetry) { firstUsePreferences.loadState.map { it } }
            val firstUseLoad = firstUseFlow.collectAsStateWithLifecycle(initialValue = FirstUseLoadState.Loading).value
            val firstUse = (firstUseLoad as? FirstUseLoadState.Ready)?.state
            val setupInputsFlow =
                remember(firstUseRetry) {
                    combine(preferences.state, sunriseState) { preference, alarm ->
                        val configurations =
                            studio.hypertext.curfew.checkin
                                .CheckInCatalog(this@MainActivity)
                        SetupInputs(
                            preference,
                            preference.selectedCallbackId?.let { configurations.find(it) },
                            alarm?.schedule?.callbackId?.let { configurations.find(it) },
                        )
                    }.map<SetupInputs, Result<SetupInputs>?> { Result.success(it) }
                        .catch { emit(Result.failure(it)) }
                }
            val setupInputsResult = setupInputsFlow.collectAsStateWithLifecycle(initialValue = null).value
            val runtime = remember { AlarmRuntimeRepository(this@MainActivity) }
            val bootId = remember(runtime) { runtime.currentBootId() }
            val campaignId =
                intent.getStringExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID)
                    ?: savedAlarm?.occurrence?.campaignId
            val campaignFlow =
                remember(campaignId, runtime) {
                    campaignId
                        ?.let { runtime.observeSnapshot(it) }
                        ?.catch {
                            sunriseError = "Couldn’t load this alarm."
                            emit(null)
                        } ?: flowOf(null)
                }
            val campaignSnapshot =
                remember(campaignFlow) { campaignFlow.map { true to it } }
                    .collectAsStateWithLifecycle(initialValue = false to null)
                    .value
            val runtimeSnapshot = if (campaignSnapshot.first) campaignSnapshot.second else requestedAlarmSnapshot
            val campaign = runtimeSnapshot?.state
            val showAlarm = campaign is AlarmCampaignState.Ringing || campaign is AlarmCampaignState.Quiet
            val initialDataReady =
                requestedAlarmLoaded && (
                    showAlarm ||
                        (
                            savedAlarmResult.first && firstUseLoad != FirstUseLoadState.Loading && campaignSnapshot.first &&
                                setupInputsResult != null
                        )
                )
            var ringingCondition by remember(campaignId) { mutableStateOf<studio.hypertext.curfew.checkin.CheckInDescription?>(null) }
            LaunchedEffect(campaignId) {
                ringingCondition =
                    campaignId?.let { id ->
                        withContext(Dispatchers.IO) {
                            AlarmRuntimeRepository(this@MainActivity).callbackId(id)?.let {
                                studio.hypertext.curfew.checkin
                                    .CheckInCatalog(this@MainActivity)
                                    .find(it)
                            }
                        }
                    }
            }
            LaunchedEffect(showAlarm) {
                if (showAlarm) {
                    presentOverKeyguard()
                } else {
                    setShowWhenLocked(false)
                    setTurnScreenOn(false)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            BackHandler(enabled = recoveryVisible) {
                ownerRecovery.close()
                recoveryVisible = false
            }
            LaunchedEffect(showAlarm, campaignId) {
                if (!showAlarm) {
                    if (placeScanRequest?.campaignId != null && !placeScanCommitting) placeScanRequest = null
                    recoveryVisible = false
                    if (ownerRecoveryDelegate.isInitialized()) ownerRecovery.alarmEnded()
                }
            }
            CurfewTheme {
                val soundCheckState = soundCheck.state.collectAsStateWithLifecycle().value
                if (!initialDataReady) {
                    Surface(
                        modifier =
                            androidx.compose.ui.Modifier
                                .fillMaxSize(),
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                } else if (placeScanRequest != null) {
                    val request = requireNotNull(placeScanRequest)
                    studio.hypertext.curfew.ui.PlaceScanScreen(request.label, onScan = { payload ->
                        placeScanCommitting = true
                        try {
                            when {
                                request.draftId != null -> {
                                    places.enroll(request.draftId, payload)
                                }

                                request.codeId != null -> {
                                    val enrolled = requireNotNull(places.find(request.codeId)) { "This code was removed." }
                                    studio.hypertext.curfew.checkin.PlaceCheckIn.enroll(
                                        studio.hypertext.curfew.checkin
                                            .PlaceDraft(enrolled, payload),
                                        payload,
                                    )
                                }

                                request.campaignId != null -> {
                                    val result = places.approve(request.campaignId, payload)
                                    val message =
                                        when (result) {
                                            PlaceApprovalResult.CHECKED_IN -> "Checked in."
                                            PlaceApprovalResult.NOT_ACTIVE -> "This alarm is no longer active."
                                            PlaceApprovalResult.NEXT_ALARM_UNAVAILABLE -> "Checked in. Check tomorrow’s alarm."
                                        }
                                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                                }
                            }
                            placeScanRequest = null
                            placeEpoch++
                            capabilityEpoch++
                        } finally {
                            placeScanCommitting = false
                        }
                    }, onCancel = { placeScanRequest = null })
                } else if (showAlarm && recoveryVisible) {
                    AlarmRecoveryScreen(
                        conditionLabel = ringingCondition?.displayLabel ?: "your check-in",
                        credentialAvailable = ownerRecovery.credentialAvailable,
                        busy = ownerRecovery.busy,
                        error = ownerRecovery.error,
                        onBack = {
                            ownerRecovery.close()
                            recoveryVisible = false
                        },
                        onBeginHold = { reason -> campaignId?.let { ownerRecovery.beginHold(it, reason) } },
                        onCancelHold = ownerRecovery::cancelHold,
                        onConfirm = ownerRecovery::requestConfirmation,
                        onSecuritySettings = { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                    )
                } else if (showAlarm) {
                    val clock =
                        android.text.format.DateFormat
                            .getTimeFormat(this@MainActivity)
                    AlarmRingingScreen(
                        time = clock.format(java.util.Date.from(requireNotNull(campaign).campaignStartedAt())),
                        nextRingTime =
                            runtimeSnapshot.takeIf { it.state is AlarmCampaignState.Quiet }?.let {
                                AlarmRuntimeClock
                                    .nextTransitionAt(
                                        it,
                                        java.time.Instant.now(),
                                        android.os.SystemClock.elapsedRealtime(),
                                        bootId,
                                    )?.let { next -> clock.format(java.util.Date.from(next)) }
                            },
                        onRecover = { recoveryVisible = true },
                        conditionLabel = ringingCondition?.displayLabel ?: "your check-in",
                        actionLabel =
                            if (ringingCondition?.placeCode ==
                                true
                            ) {
                                "Scan code"
                            } else {
                                ringingCondition?.actionUrl?.let { "Open check-in" }
                            },
                        onOpenAction = {
                            if (ringingCondition?.placeCode == true) {
                                placeScanRequest = PlaceScanRequest(ringingCondition?.displayLabel ?: "saved", campaignId = campaignId)
                            }
                            ringingCondition?.actionUrl?.let {
                                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                                    .onFailure {
                                        Toast
                                            .makeText(
                                                this@MainActivity,
                                                "Couldn’t open the check-in link.",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                    }
                            }
                        },
                    )
                } else if (sunriseReadFailed || (firstUseLoad == FirstUseLoadState.Error && savedAlarm == null) ||
                    setupInputsResult?.isFailure == true
                ) {
                    Surface { FirstUseLoadErrorScreen(onRetry = { firstUseRetry++ }) }
                } else {
                    val inputs = requireNotNull(setupInputsResult).getOrThrow()
                    val preferenceState = inputs.preferences
                    val readiness = currentReadiness(preferenceState)
                    val conditionLabel = inputs.selected?.displayLabel
                    CurfewApp(
                        soundCheckState = soundCheckState,
                        onConfirmSound = { lifecycleScope.launch { soundCheck.confirm() } },
                        onCancelSound = ::cancelTestSound,
                        readiness = readiness,
                        showWelcome = firstUse?.needsWelcome(savedAlarm != null) == true,
                        startSetup = savedAlarm == null && firstUse?.choice == FirstUseChoice.STARTED,
                        setupDraft = firstUse?.draft.takeIf { savedAlarm == null },
                        onWelcomeChoice = { start ->
                            if (start) firstUsePreferences.chooseContinue() else firstUsePreferences.chooseNotNow()
                        },
                        onPrepareDraft = { draft -> firstUsePreferences.saveDraft(draft) },
                        onSetupActivated = { lifecycleScope.launch { runCatching { firstUsePreferences.clearDraft() } } },
                        accountEnrolled = preferenceState.accountEnrolled,
                        accountSignedIn = preferenceState.accountSignedIn,
                        accountRecoveryState = accountRecoveryState.takeIf { preferenceState.accountSignedIn },
                        onGrantExactAlarm = ::requestExactAlarmAccess,
                        onGrantNotifications = ::requestNotifications,
                        onGrantFullScreen = ::requestFullScreenAccess,
                        onRepairAlarmChannel = ::openAlarmChannelSettings,
                        onRaiseAlarmVolume = { startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) },
                        onPlayTestSound = ::playTestSound,
                        onAcknowledgeLimitations = {
                            lifecycleScope.launch { preferences.acknowledgeLimitations() }
                        },
                        onOpenAccount = {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(ProductionServices.ACCOUNT_PORTAL),
                                ),
                            )
                        },
                        onEnrollAccount = {
                            lifecycleScope.launch {
                                runCatching {
                                    BrowserOAuthEnrollment(this@MainActivity).begin()
                                }.onFailure {
                                    Toast
                                        .makeText(
                                            this@MainActivity,
                                            "Couldn’t connect your account. Try again.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                }
                            }
                        },
                        onRestoreRecoveryKey = { encodedKey ->
                            lifecycleScope.launch {
                                runCatching {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        AndroidAccountRecovery(this@MainActivity).restore(encodedKey)
                                    }
                                }.onSuccess { state ->
                                    accountRecoveryState = state
                                    preferences.setAccountEnrolled(true)
                                }.onFailure {
                                    Toast
                                        .makeText(
                                            this@MainActivity,
                                            "That recovery key couldn’t restore your settings.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                }
                            }
                        },
                        onAcknowledgeRecoveryKey = {
                            lifecycleScope.launch {
                                AndroidAccountRecovery(this@MainActivity)
                                    .acknowledgeSavedRecoveryKey()
                                accountRecoveryState = AccountRecoveryState.Ready
                                preferences.setAccountEnrolled(true)
                            }
                        },
                        onSaveCallback = { label, endpoint, actionUrl ->
                            conditionBusy = true
                            conditionMessage = null
                            try {
                                wakeConditionSetup.save(label, endpoint, actionUrl).callbackId
                            } finally {
                                conditionBusy = false
                            }
                        },
                        conditionVerified =
                            inputs.selected?.available == true &&
                                (inputs.selected.placeCode.not() || places.cameraReady()) && preferenceState.selectedCallbackId?.let {
                                    preferenceState.isCallbackReady(it)
                                } ?: false,
                        conditionBusy = conditionBusy,
                        conditionMessage = conditionMessage,
                        conditionSetupFileSaved = exportedVerifierId != null && exportedVerifierId == preferenceState.selectedCallbackId,
                        onExportCondition = {
                            val id = preferenceState.selectedCallbackId
                            if (id != null && !conditionBusy) {
                                lifecycleScope.launch {
                                    conditionBusy = true
                                    try {
                                        wakeConditionSetup.provision(id)
                                        pendingVerifierExportId = id
                                        exportVerifier.launch("curfew-check-in.json")
                                    } catch (failure: Exception) {
                                        pendingVerifierExportId = null
                                        conditionMessage = "Couldn’t read this check-in. Save it again before saving its setup file."
                                    } finally {
                                        conditionBusy = false
                                    }
                                }
                            }
                        },
                        onVerifyCondition = {
                            val id = preferenceState.selectedCallbackId
                            if (id != null && !conditionBusy) {
                                lifecycleScope.launch {
                                    conditionBusy = true
                                    conditionMessage = "Testing the connection…"
                                    try {
                                        conditionMessage =
                                            if (wakeConditionSetup.verify(id)) {
                                                null
                                            } else {
                                                "Your check-in changed. Test the connection again."
                                            }
                                    } catch (failure: Exception) {
                                        conditionMessage =
                                            "Couldn’t connect. Ask the person running your service to check its setup, then try again."
                                    } finally {
                                        conditionBusy = false
                                    }
                                }
                            }
                        },
                        placeCodeSelected = inputs.selected?.placeCode == true,
                        placeCheckInContent = { advanced, done, useForAlarm ->
                            studio.hypertext.curfew.ui.PlaceCheckInDestination(
                                selectedId = preferenceState.selectedCallbackId,
                                refresh = placeEpoch + capabilityEpoch,
                                externalBusy = placeExportBusy,
                                externalError = placeExportError,
                                onExport = { draft ->
                                    if (!placeExportBusy) {
                                        placeExportError = null
                                        pendingPlaceExportId = draft.enrollment.id
                                        placeExportBusy = true
                                        exportPlace.launch("curfew-check-in-code.png")
                                    }
                                },
                                onPrint = { draft ->
                                    lifecycleScope.launch {
                                        try {
                                            val bitmap =
                                                withContext(
                                                    Dispatchers.Default,
                                                ) {
                                                    studio.hypertext.curfew.checkin.PlaceCodeImage
                                                        .create(draft.payload)
                                                }
                                            try {
                                                (getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager)
                                                    .print(
                                                        "Curfew check-in",
                                                        studio.hypertext.curfew.checkin
                                                            .PlaceCodePrinter(this@MainActivity, bitmap),
                                                        null,
                                                    )
                                            } catch (failure: Exception) {
                                                bitmap.recycle()
                                                throw failure
                                            }
                                        } catch (
                                            cancelled: kotlinx.coroutines.CancellationException,
                                        ) {
                                            throw cancelled
                                        } catch (
                                            _: Exception,
                                        ) {
                                            placeExportError = "Couldn’t open printing. Try Save code instead."
                                        }
                                    }
                                },
                                onScan = { draft, enrollment ->
                                    placeScanRequest =
                                        when {
                                            draft != null -> PlaceScanRequest(draft.enrollment.label, draftId = draft.enrollment.id)
                                            enrollment != null -> PlaceScanRequest(enrollment.label, codeId = enrollment.id)
                                            else -> null
                                        }
                                },
                                onAdvanced = advanced,
                                onDone = done,
                                onUseForAlarm = useForAlarm,
                            )
                        },
                        sunriseAlarm = savedAlarm,
                        callbackId = preferenceState.selectedCallbackId,
                        conditionLabel = if (sunriseReadFailed) null else conditionLabel,
                        alarmConditionLabel = inputs.alarmConnection?.displayLabel,
                        alarmBusy = sunriseSaving,
                        alarmError = sunriseError,
                        onSaveAlarm = ::saveSunrise,
                        onToggleAlarm = { enabled -> changeSunrise(enabled = enabled) },
                    )
                }
            }
        }
    }

    override fun onStop() {
        cancelTestSound()
        super.onStop()
    }

    override fun onDestroy() {
        if (ownerRecoveryDelegate.isInitialized()) ownerRecovery.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        capabilityEpoch += 1
        lifecycleScope.launch {
            runCatching { sunrise.restore() }.onFailure {
                sunriseError =
                    if (!AndroidAlarmClockGateway(this@MainActivity).canScheduleExactAlarms()) {
                        "Alarm access is off."
                    } else {
                        "Couldn’t restore this alarm."
                    }
            }
            PerpetualAlarmService.restoreAfterForegroundEntry(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleOAuthCallback(intent)) {
            setIntent(Intent(this, MainActivity::class.java))
        } else {
            setIntent(intent)
            recreate()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Keep only the identity across the picker; reload secret material after its result.
        outState.putString("pending_place_export", pendingPlaceExportId)
        outState.putString("pending_check_in_export", pendingVerifierExportId)
        outState.putString("saved_check_in_export", exportedVerifierId)
        super.onSaveInstanceState(outState)
    }

    /**
     * Present only the active alarm over the keyguard. Requesting keyguard
     * dismissal here opens a credential prompt before the owner can reach the
     * alarm's actions. Recovery requests its own fresh confirmation separately.
     * Launcher entry must keep normal lock-screen behavior.
     */
    private fun presentOverKeyguard() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun handleOAuthCallback(callbackIntent: Intent): Boolean {
        val callback = callbackIntent.data ?: return false
        if (
            callback.scheme != "studio.hypertext.curfew" ||
            callback.host != "oauth" ||
            callback.path != "/callback"
        ) {
            return false
        }
        lifecycleScope.launch {
            runCatching { OAuthTokenExchange(this@MainActivity).exchange(callback) }
                .onSuccess { state ->
                    accountRecoveryState = state
                    if (state == AccountRecoveryState.Ready) {
                        preferences.setAccountEnrolled(true)
                    }
                    capabilityEpoch += 1
                    Toast
                        .makeText(
                            this@MainActivity,
                            when (state) {
                                AccountRecoveryState.Ready -> {
                                    "Signed in."
                                }

                                AccountRecoveryState.EnterRecoveryKey -> {
                                    "Signed in. Enter your Curfew Recovery Key to restore your settings."
                                }

                                is AccountRecoveryState.SaveRecoveryKey -> {
                                    "Signed in. Save your Curfew Recovery Key to protect your settings."
                                }
                            },
                            Toast.LENGTH_LONG,
                        ).show()
                }.onFailure {
                    Toast
                        .makeText(
                            this@MainActivity,
                            "Sign-in could not be completed. Please try again.",
                            Toast.LENGTH_LONG,
                        ).show()
                }
        }
        return true
    }

    private fun currentReadiness(preferenceState: CurfewPreferenceState): AlarmReadiness =
        studio.hypertext.curfew.platform
            .AndroidAlarmReadiness(this)
            .evaluate(preferenceState)

    private fun requestExactAlarmAccess() {
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestFullScreenAccess() {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }
    }

    private fun requestNotifications() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST,
        )
    }

    private fun openAlarmChannelSettings() {
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, PerpetualAlarmService.ALARM_CHANNEL_ID),
        )
    }

    private fun playTestSound() {
        testSoundJob?.cancel()
        testSoundJob = lifecycleScope.launch { soundCheck.play() }
    }

    private fun cancelTestSound() {
        testSoundJob?.cancel()
        testSoundJob = null
        soundCheck.close()
    }

    private fun saveSunrise(schedule: SunriseSchedule) {
        changeSunrise(schedule = schedule)
    }

    private fun changeSunrise(
        schedule: SunriseSchedule? = null,
        enabled: Boolean? = null,
    ) {
        if (sunriseSaving || sunriseReadFailed) return
        sunriseSaving = true
        sunriseError = null
        lifecycleScope.launch {
            try {
                val desired =
                    schedule ?: sunrise.state.first()?.schedule?.copy(
                        enabled = requireNotNull(enabled),
                    ) ?: return@launch
                sunrise.save(desired)
            } catch (failure: Exception) {
                sunriseError = alarmSaveError(failure)
            } finally {
                sunriseSaving = false
            }
        }
    }

    companion object {
        const val EXTRA_SHOW_ALARM = "show_alarm"
        private const val NOTIFICATION_PERMISSION_REQUEST = 41
    }
}
