package studio.hypertext.curfew.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.R

@Composable
internal fun CurfewWelcomeScreen(onContinue: () -> Unit, onNotNow: () -> Unit) {
    TaskContent(spacing = 32.dp, verticalPadding = 32.dp) {
        Box(Modifier.size(96.dp).clip(MaterialTheme.shapes.extraLarge)) {
            Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.matchParentSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.matchParentSize())
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Welcome to Curfew", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
            Text(
                "Your alarm keeps ringing until you complete your check-in.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Set up alarm") }
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Not now") }
        }
    }
}
