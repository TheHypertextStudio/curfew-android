package studio.hypertext.curfew.ui

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.hypertext.curfew.R
import studio.hypertext.curfew.checkin.*

@Composable
internal fun PlaceCheckInDestination(
    selectedId: String?, refresh: Int, externalBusy: Boolean = false, externalError: String? = null,
    onExport: (PlaceDraft) -> Unit, onPrint: (PlaceDraft) -> Unit, onScan: (PlaceDraft?, PlaceEnrollment?) -> Unit,
    onAdvanced: () -> Unit, onDone: () -> Unit, onUseForAlarm: (() -> Unit)?,
) {
    val context = LocalContext.current
    val repository = remember { PlaceCheckInRepository(context) }
    var draft by remember { mutableStateOf<PlaceDraft?>(null) }
    var enrollment by remember { mutableStateOf<PlaceEnrollment?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        draft = repository.draft()
        enrollment = selectedId?.let { repository.find(it) }
    }
    LaunchedEffect(selectedId, refresh) {
        try { load() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Couldn’t read this code. Replace it or use an advanced integration." }
        finally { loading = false }
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block(); load() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Couldn’t save this change. Try again." }
            finally { busy = false }
        }
    }
    if (loading) { CircularProgressIndicator(); return }
    PlaceCheckInSetup(draft, enrollment, busy || externalBusy, error ?: externalError,
        onCreate = { name -> action { draft = repository.begin(name) } },
        onExport = { draft?.let(onExport) }, onPrint = { draft?.let(onPrint) },
        onScan = { onScan(draft, enrollment) },
        onCancel = { action { repository.cancelDraft() } },
        onReplace = { confirmation = "replace" }, onRemove = { confirmation = "remove" },
        onAdvanced = onAdvanced, onDone = onDone, onUseForAlarm = onUseForAlarm,
        cameraReady = repository.cameraReady(),
        onRemoveUnavailable = if (error != null && selectedId != null && enrollment == null) ({ confirmation = "remove" }) else null,
    )
    confirmation?.let { operation ->
        AlertDialog(onDismissRequest = { if (!busy) confirmation = null },
            title = { Text(if (operation == "replace") "Replace this code?" else "Remove this code?") },
            text = { Text("Future alarms using this code will turn off. A ringing alarm stays active and may need Alarm recovery. The old code will no longer work.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                val id = enrollment?.id ?: selectedId
                confirmation = null
                action {
                    if (id != null) repository.remove(id)
                    enrollment = null
                    if (operation == "replace") draft = repository.begin("Bathroom")
                }
            }) { Text(if (operation == "replace") "Replace code" else "Remove code") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Keep code") } })
    }
}

@Composable
internal fun PlaceCheckInSetup(
    draft: PlaceDraft?, enrollment: PlaceEnrollment?, busy: Boolean, error: String?,
    onCreate: (String) -> Unit, onExport: () -> Unit, onPrint: () -> Unit, onScan: () -> Unit,
    onCancel: () -> Unit, onReplace: () -> Unit, onRemove: () -> Unit,
    onAdvanced: () -> Unit, onDone: () -> Unit, onUseForAlarm: (() -> Unit)?, cameraReady: Boolean,
    onRemoveUnavailable: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf("Bathroom") }
    val retryRegion = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null) { withFrameNanos { }; retryRegion.bringIntoView() } }
    SetupTaskContent(introduction = {
        SetupIntroduction(R.drawable.ic_link_rounded,
            if (draft != null) "Put your code away from bed" else if (enrollment != null) enrollment.label else "Scan a code away from bed",
            if (draft != null) "Save or print the code, place it away from bed, then scan it to finish setup."
            else if (enrollment != null) "Scan your code when Sunrise rings. No internet connection needed."
            else "Put a code in the bathroom or another place you’ll get up to reach. Scan it each morning to stop Sunrise.")
    }) {
        when {
            draft != null -> {
                Text("Keep this code private.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth().bringIntoViewRequester(retryRegion)) {
                    error?.let { TaskMessage(it, error = true) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Save code") }
                        OutlinedButton(onClick = onPrint, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Print code") }
                }
                }
                if (draft.exported) Text("Code saved.")
                Button(onClick = onScan, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Scan code") }
                TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel code setup") }
            }
            enrollment != null -> {
                Text(if (cameraReady) "Ready for your alarm" else "Camera access is needed to scan your code.", style = MaterialTheme.typography.titleMedium)
                Button(onClick = if (cameraReady) onDone else onScan, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (cameraReady) "Continue" else "Allow camera access") }
                onUseForAlarm?.let { Button(onClick = it, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Use for alarm") } }
                OutlinedButton(onClick = onReplace, enabled = !busy) { Text("Replace code") }
                TextButton(onClick = onRemove, enabled = !busy) { Text("Remove code") }
            }
            else -> {
                OutlinedTextField(name, { if (it.length <= 60) name = it }, label = { Text("Place name") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                Column(Modifier.fillMaxWidth().bringIntoViewRequester(retryRegion)) {
                    error?.let { TaskMessage(it, error = true) }
                    Button(onClick = { onCreate(name.trim()) }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (busy) "Creating…" else "Create code") }
                }
            }
        }
        if (enrollment != null && draft == null) error?.let { TaskMessage(it, error = true) }
        onRemoveUnavailable?.let { TextButton(onClick = it, enabled = !busy) { Text("Remove unavailable code") } }
        if (draft == null) TextButton(onClick = onAdvanced, enabled = !busy) { Text("Advanced integration") }
    }
}
