package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun CheckInScreen(
    callbackId: String?,
    savedLabel: String?,
    verified: Boolean,
    busy: Boolean,
    message: String?,
    onSave: (String, String, String?) -> Unit,
    onExport: () -> Unit,
    onVerify: () -> Unit,
    onDone: () -> Unit,
) {
    var replacing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("") }
    var actionUrl by rememberSaveable { mutableStateOf("") }
    var submittedId by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var awaitingSelection by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val endpointValid = validServiceAddress(endpoint)
    val actionValid = actionUrl.isBlank() || runCatching {
        java.net.URI(actionUrl.trim())
        true
    }.getOrDefault(false)
    LaunchedEffect(callbackId, busy, submitting, awaitingSelection) {
        if (awaitingSelection && callbackId != submittedId) {
            awaitingSelection = false
            replacing = false
        }
        if (submitting && !busy) submitting = false
    }
    TaskContent {
        if (savedLabel != null && !replacing) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(savedLabel, style = MaterialTheme.typography.headlineSmall)
                Text(if (verified) "Connected" else "Connection not tested", color = if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("This service can stop Sunrise after your check-in. If it cannot be reached, the alarm stays active.")
            if (!verified) {
                Text("Give your service its setup file, then test the connection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Export setup file") }
                Button(onClick = onVerify, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (busy) "Checking…" else "Test connection") }
            }
            if (message != null) TaskMessage(message)
            TextButton(onClick = { showDetails = !showDetails }, enabled = !busy) { Text("Connection details") }
            if (showDetails) {
                Text("The setup file contains a private signing key. Give it only to the service you chose.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (verified) OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Export setup file") }
                if (verified) OutlinedButton(onClick = onVerify, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Test connection") }
                TextButton(onClick = { replacing = true; name = ""; endpoint = ""; actionUrl = "" }, enabled = !busy) { Text("Replace check-in") }
            }
            if (verified) Button(onClick = onDone, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Done") }
        } else {
            Text("Your service confirms your check-in before Sunrise can stop. If it cannot be reached, the alarm stays active.", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                name, { name = it }, label = { Text("Name") }, enabled = !busy && !submitting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                endpoint, { endpoint = it }, label = { Text("Service address") }, enabled = !busy && !submitting,
                supportingText = if (endpoint.isNotBlank() && !endpointValid) { { Text("Enter an HTTPS address supplied by your service.") } } else null,
                isError = endpoint.isNotBlank() && !endpointValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                actionUrl, { actionUrl = it }, label = { Text("Check-in link (optional)") }, enabled = !busy && !submitting,
                supportingText = { Text(if (!actionValid) "Enter a valid link." else "Open this link when Sunrise rings.") },
                isError = !actionValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (message != null) TaskMessage(message)
            Button(
                onClick = {
                    submittedId = callbackId
                    submitting = true
                    awaitingSelection = true
                    onSave(name.trim(), endpoint.trim(), actionUrl.trim().takeIf(String::isNotBlank))
                }, enabled = !busy && !submitting && name.isNotBlank() && endpointValid && actionValid,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(if (busy || submitting) "Saving…" else "Save check-in") }
            if (replacing) TextButton(onClick = { replacing = false }, enabled = !busy && !submitting) { Text("Keep current check-in") }
        }
    }
}

private fun validServiceAddress(value: String): Boolean = runCatching {
    java.net.URI(value.trim()).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }
}.getOrDefault(false)
