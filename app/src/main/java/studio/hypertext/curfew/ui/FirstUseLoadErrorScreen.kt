package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun FirstUseLoadErrorScreen(onRetry: () -> Unit) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            TaskContent {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("Couldn’t load setup", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Try again") }
                    }
                }
            }
        }
    }
}
