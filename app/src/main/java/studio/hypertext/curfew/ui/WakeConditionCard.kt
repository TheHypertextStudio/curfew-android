package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun WakeConditionCard(
    savedLabel: String?,
    verified: Boolean,
    busy: Boolean,
    message: String?,
    onSave: (String, String, String?) -> Unit,
    onExport: () -> Unit,
    onVerify: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("") }
    var actionUrl by rememberSaveable { mutableStateOf("") }
    val endpointValid = runCatching {
        java.net.URI(endpoint).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }
    }.getOrDefault(false)
    val control = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    CurfewCard("Wake condition") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connect a trusted HTTPS verifier to release Sunrise. An unavailable verifier leaves the alarm active.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            savedLabel?.let { Text(if (verified) "$it · Connection verified" else "$it · Connection not tested") }
            OutlinedTextField(label, { label = it }, label = { Text("Condition name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS verifier endpoint") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                isError = endpoint.isNotBlank() && !endpointValid)
            OutlinedTextField(actionUrl, { actionUrl = it }, label = { Text("Optional action URL") },
                supportingText = { Text("Open this link from the ringing screen.") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            OutlinedButton({ onSave(label.trim(), endpoint.trim(), actionUrl.trim().takeIf(String::isNotBlank)) },
                enabled = !busy && label.isNotBlank() && endpointValid, modifier = control) {
                Text(if (busy) "Working…" else "Save condition")
            }
            Text("Export the private setup file to your verifier, then test the connection. The file contains its signing key; keep it private.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onExport, enabled = !busy && savedLabel != null, modifier = control) {
                Text("Export verifier setup")
            }
            OutlinedButton(onVerify, enabled = !busy && savedLabel != null, modifier = control) {
                Text("Test connection")
            }
            message?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
}
