package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import studio.hypertext.curfew.platform.PerpetualAlarmService

@Composable
internal fun RestoreOnResumeEffect(onResume: () -> Unit) {
    val context = LocalContext.current.applicationContext
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onResume()
        PerpetualAlarmService.restoreAfterForegroundEntry(context)
    }
}
