package studio.hypertext.curfew.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
internal fun ShowOverKeyguardEffect(activity: Activity, active: Boolean) {
    if (!active) return
    DisposableEffect(activity) {
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setTurnScreenOn(false)
            activity.setShowWhenLocked(false)
        }
    }
}
