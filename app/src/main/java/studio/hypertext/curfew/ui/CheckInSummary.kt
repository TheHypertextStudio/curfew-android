package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.R

@Composable
internal fun CheckInSummary(label: String, verified: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth().curfewSharedBounds("check-in-summary"),
        shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(if (verified) R.drawable.ic_check_circle_rounded else R.drawable.ic_link_rounded), null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                Text(if (verified) "Connected" else "Connection not tested", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
