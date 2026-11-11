package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import studio.hypertext.curfew.platform.PerpetualAlarmService

/**
 * Runs Sunrise restore and the playback recovery check when the Activity resumes. It uses a
 * lifecycle event because these are one-shot requests rather than resources that need a pause
 * callback.
 */
@Composable
internal fun RestoreOnResumeEffect(onResume: () -> Unit) {
    val context = LocalContext.current.applicationContext
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onResume()
        PerpetualAlarmService.restoreAfterForegroundEntry(context)
    }
}
