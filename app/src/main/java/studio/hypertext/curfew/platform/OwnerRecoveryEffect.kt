package studio.hypertext.curfew.platform

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import studio.hypertext.curfew.alarm.OwnerRecoveryResult

/**
 * Creates the credential controller for the current alarm window. Disposing presentation
 * cancels an unused credential prompt without interrupting an already authorized durable
 * recovery.
 */
@Composable
internal fun rememberOwnerRecovery(onRecovered: (OwnerRecoveryResult) -> Unit): AndroidOwnerRecovery? {
    val activity = LocalActivity.current as? ComponentActivity ?: return null
    val latestResult by rememberUpdatedState(onRecovered)
    val recovery = remember(activity) { AndroidOwnerRecovery(activity, onRecovered = { latestResult(it) }) }
    DisposableEffect(recovery) {
        onDispose { recovery.alarmEnded() }
    }
    return recovery
}
