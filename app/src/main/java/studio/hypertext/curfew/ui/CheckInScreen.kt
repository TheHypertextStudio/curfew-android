package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Stable
internal class CheckInState {
    var editing by mutableStateOf(false)
    var name by mutableStateOf("")
    var endpoint by mutableStateOf("")
    var actionUrl by mutableStateOf("")
    var optionalDetails by mutableStateOf(false)
    var connectionDetails by mutableStateOf(false)
    var completing by mutableStateOf(false)
    var pendingId by mutableStateOf<String?>(null)
    var advanced by mutableStateOf(false)
    var preferCode by mutableStateOf(false)

    companion object {
        val Saver = listSaver<CheckInState, String>(
            save = { listOf(it.editing.toString(), it.name, it.endpoint, it.actionUrl, it.optionalDetails.toString(), it.connectionDetails.toString(), it.completing.toString(), it.pendingId.orEmpty(), it.advanced.toString(), it.preferCode.toString()) },
            restore = { values -> CheckInState().apply {
                editing = values[0].toBoolean(); name = values[1]; endpoint = values[2]; actionUrl = values[3]
                optionalDetails = values[4].toBoolean(); connectionDetails = values[5].toBoolean(); completing = values[6].toBoolean(); pendingId = values[7].takeIf(String::isNotBlank); advanced = values.getOrNull(8)?.toBoolean() ?: false; preferCode = values.getOrNull(9)?.toBoolean() ?: false
            } },
        )
    }
}

@Composable
internal fun rememberCheckInState() = rememberSaveable(saver = CheckInState.Saver) { CheckInState() }

@Composable
internal fun CheckInScreen(
    callbackId: String?, savedLabel: String?, verified: Boolean, busy: Boolean, message: String?,
    onSave: suspend (String, String, String?) -> String, onExport: () -> Unit, onVerify: () -> Unit, onDone: () -> Unit,
    inSetup: Boolean = false,
    state: CheckInState = rememberCheckInState(),
    onDefer: (() -> Unit)? = null,
    deferBusy: Boolean = false,
    deferError: String? = null,
    onUseForAlarm: (() -> Unit)? = null,
    inlineHeader: (@Composable () -> Unit)? = null,
    setupFileSaved: Boolean = false,
    placeCodeSelected: Boolean = false,
    consumerContent: (@Composable (() -> Unit, () -> Unit, (() -> Unit)?) -> Unit)? = null,
) {
    if (consumerContent != null && !state.advanced && (callbackId == null || savedLabel == null || placeCodeSelected || state.preferCode)) {
        consumerContent({ state.advanced = true; state.preferCode = false }, onDone, onUseForAlarm)
        return
    }
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val waiting = submitting || state.pendingId != null
    val working = busy || waiting || deferBusy
    val connected = callbackId != null && savedLabel != null
    val endpointValid = validServiceAddress(state.endpoint)
    val actionValid = state.actionUrl.isBlank() || runCatching { java.net.URI(state.actionUrl.trim()).isAbsolute }.getOrDefault(false)
    LaunchedEffect(callbackId, savedLabel, state.pendingId) {
        if (state.pendingId != null && callbackId == state.pendingId && savedLabel != null) {
            state.editing = false
            state.completing = true
            state.connectionDetails = false
            state.pendingId = null
        }
    }
    when {
        state.editing -> SetupTaskContent(
            introduction = { if (inlineHeader != null) inlineHeader() else SetupIntroduction(R.drawable.ic_link_rounded, "Connect your service", "Ask the person running your service for its Curfew connection address. A normal website address won’t work.") },
        ) {
            OutlinedTextField(state.endpoint, { state.endpoint = it }, label = { Text("Service address") }, enabled = !working,
                supportingText = if (state.endpoint.isNotBlank() && !endpointValid) { { Text("Enter an HTTPS address.") } } else null,
                isError = state.endpoint.isNotBlank() && !endpointValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { state.optionalDetails = !state.optionalDetails }, enabled = !working) { Text("Optional details") }
            if (state.optionalDetails) {
                OutlinedTextField(state.name, { state.name = it }, label = { Text("Name") }, placeholder = { Text("Morning check-in") }, enabled = !working,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.actionUrl, { state.actionUrl = it }, label = { Text("Check-in link (optional)") }, enabled = !working,
                    supportingText = { Text(if (!actionValid) "Enter a valid link." else "Opens your service when the alarm rings.") }, isError = !actionValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            (saveError ?: message)?.let { TaskMessage(it, error = true) }
            Button(onClick = {
                if (working) return@Button
                submitting = true
                saveError = null
                scope.launch {
                    try {
                        state.pendingId = onSave(state.name.trim().ifBlank { "Morning check-in" }, state.endpoint.trim(), state.actionUrl.trim().takeIf(String::isNotBlank))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        saveError = "Couldn’t save the check-in. Try again."
                    } finally { submitting = false }
                }
            }, enabled = !working && endpointValid && actionValid, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (working) "Saving…" else "Save check-in")
            }
            if (connected) TextButton(onClick = { state.editing = false }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Keep current check-in") }
        }
        connected -> SetupTaskContent(
            introduction = {
                CheckInSummary(requireNotNull(savedLabel), verified)
                if (!verified) {
                    Text("Give the setup file to the person running your service. After they install it, test the connection.", style = MaterialTheme.typography.bodyLarge)
                    Text("Keep the file private. It allows your service to stop the alarm.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        ) {
            if (!verified) {
                CheckInAction("Save setup file", primary = !setupFileSaved, enabled = !working, onClick = onExport)
                CheckInAction(if (busy) "Checking…" else "Test connection", primary = setupFileSaved, enabled = !working) { state.completing = true; onVerify() }
            }
            if (message != null) TaskMessage(message)
            if (verified && (state.completing || inSetup)) Button(onClick = { state.completing = false; onDone() }, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (inSetup) "Continue" else "Done") }
            if (verified && onUseForAlarm != null) Button(onClick = onUseForAlarm, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Use for alarm") }
            if (consumerContent != null) TextButton(onClick = { state.preferCode = true; state.advanced = false }, enabled = !working) { Text("Use a code instead") }
            TextButton(onClick = { state.connectionDetails = !state.connectionDetails }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Connection details") }
            if (state.connectionDetails) {
                Text("The setup file allows your service to stop your alarm. Share it only with that service.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (verified) {
                    OutlinedButton(onClick = onExport, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save setup file") }
                    OutlinedButton(onClick = onVerify, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Test connection") }
                }
                TextButton(onClick = { state.editing = true; state.name = ""; state.endpoint = ""; state.actionUrl = ""; state.optionalDetails = false }, enabled = !working) { Text("Replace check-in") }
            }
        }
        else -> SetupTaskContent(
            introduction = { SetupIntroduction(R.drawable.ic_link_rounded, "Connect an existing service", "The alarm keeps ringing until your service confirms you’re up.") },
        ) {
            Text("For a compatible HTTPS service you or a trusted operator already run.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (inSetup && onDefer != null) {
                Text("Save your wake time for later. The alarm will stay off.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val retryRegion = remember { BringIntoViewRequester() }
                Column(Modifier.fillMaxWidth().bringIntoViewRequester(retryRegion), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (deferError != null) TaskMessage(deferError, error = true)
                    Button(onClick = onDefer, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (deferBusy) "Saving…" else "Save for later") }
                }
                LaunchedEffect(deferError) {
                    if (deferError != null) {
                        withFrameNanos { }
                        retryRegion.bringIntoView()
                    }
                }
            }
            if (consumerContent != null) TextButton(onClick = { state.preferCode = true; state.advanced = false }, enabled = !working) { Text("Use a code instead") }
            OutlinedButton(onClick = { state.editing = true }, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("I have a service") }
        }
    }
}

@Composable
private fun CheckInAction(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (primary) Button(onClick, modifier, enabled) { Text(label) }
    else OutlinedButton(onClick, modifier, enabled) { Text(label) }
}

private fun validServiceAddress(value: String): Boolean = runCatching {
    java.net.URI(value.trim()).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }
}.getOrDefault(false)
