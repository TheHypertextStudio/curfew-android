package studio.hypertext.curfew.ui

import android.content.Context
import android.graphics.Bitmap
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.checkin.PlaceCheckIn
import studio.hypertext.curfew.checkin.PlaceCheckInSetupAccess
import studio.hypertext.curfew.checkin.PlaceCodeImage
import studio.hypertext.curfew.checkin.PlaceCodePrinter
import studio.hypertext.curfew.checkin.PlaceDraft
import studio.hypertext.curfew.checkin.PlaceEnrollment

/**
 * Identifies whether a scanner is enrolling a new draft or checking an existing code. Each
 * variant carries only the data needed for that scan.
 */
private sealed interface PlaceScanTarget {
    val label: String
    data class Draft(override val label: String, val draftId: String) : PlaceScanTarget
    data class Existing(override val label: String, val enrollment: PlaceEnrollment) : PlaceScanTarget
}

/**
 * Connects place-code setup with Android's document picker, print service, and camera.
 * Persistence is supplied through the setup interface rather than constructed in Compose.
 */
@Composable
internal fun PlaceCheckInHost(
    selectedId: String?,
    places: PlaceCheckInSetupAccess,
    onAdvanced: () -> Unit,
    onDone: () -> Unit,
    onUseForAlarm: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportBusy by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var pendingExportDraft by remember { mutableStateOf<PlaceDraft?>(null) }
    var activeScanTarget by remember { mutableStateOf<PlaceScanTarget?>(null) }
    var refreshEpoch by remember { mutableIntStateOf(0) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val draft = pendingExportDraft
        pendingExportDraft = null
        if (uri == null || draft == null) {
            exportBusy = false
        } else {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val bitmap = PlaceCodeImage.create(draft.payload)
                        try {
                            requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                            }
                        } finally {
                            bitmap.recycle()
                        }
                        places.markExported(draft.enrollment.id)
                    }
                    refreshEpoch++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    exportError = "Couldn’t save the code. Choose another location and try again."
                } finally {
                    exportBusy = false
                }
            }
        }
    }

    val target = activeScanTarget
    if (target != null) {
        PlaceScanScreen(
            label = target.label,
            onScan = { payload ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            when (target) {
                                is PlaceScanTarget.Draft -> {
                                    places.enroll(target.draftId, payload)
                                }
                                is PlaceScanTarget.Existing -> {
                                    PlaceCheckIn.enroll(PlaceDraft(target.enrollment, payload), payload)
                                }
                            }
                        }
                        refreshEpoch++
                    } finally {
                        activeScanTarget = null
                    }
                }
            },
            onCancel = { activeScanTarget = null },
        )
    } else {
        PlaceCheckInDestination(
            selectedId = selectedId,
            repository = places,
            refresh = refreshEpoch,
            externalBusy = exportBusy,
            externalError = exportError,
            onExport = { draft ->
                if (!exportBusy) {
                    exportError = null
                    pendingExportDraft = draft
                    exportBusy = true
                    exportLauncher.launch("curfew-check-in-code.png")
                }
            },
            onPrint = { draft ->
                scope.launch {
                    try {
                        val bitmap = withContext(Dispatchers.Default) {
                            PlaceCodeImage.create(draft.payload)
                        }
                        try {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                            printManager?.print(
                                "Curfew check-in",
                                PlaceCodePrinter(context, bitmap),
                                null,
                            )
                        } catch (failure: Exception) {
                            bitmap.recycle()
                            throw failure
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        exportError = "Couldn’t open printing. Try Save code instead."
                    }
                }
            },
            onScan = { draft, enrollment ->
                activeScanTarget = when {
                    draft != null -> PlaceScanTarget.Draft(draft.enrollment.label, draft.enrollment.id)
                    enrollment != null -> PlaceScanTarget.Existing(enrollment.label, enrollment)
                    else -> null
                }
            },
            onAdvanced = onAdvanced,
            onDone = onDone,
            onUseForAlarm = onUseForAlarm,
        )
    }
}
