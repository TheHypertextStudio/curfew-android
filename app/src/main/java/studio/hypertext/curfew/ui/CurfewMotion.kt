package studio.hypertext.curfew.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

internal data class CurfewMotionScope(
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
)

internal val LocalCurfewMotion = staticCompositionLocalOf<CurfewMotionScope?> { null }

@Composable
internal fun Modifier.curfewSharedBounds(key: String): Modifier {
    val motion = LocalCurfewMotion.current ?: return this
    return with(motion.shared) {
        this@curfewSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = motion.visibility,
        )
    }
}
