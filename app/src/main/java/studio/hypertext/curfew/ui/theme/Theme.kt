package studio.hypertext.curfew.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CurfewAmberDark,
    onPrimary = Color(0xFF3E2E00),
    primaryContainer = CurfewAmberContainerDark,
    onPrimaryContainer = Color(0xFFFFE7A7),
    background = CurfewPaperDark,
    onBackground = CurfewInkDark,
    surface = CurfewPaperDark,
    onSurface = CurfewInkDark,
    onSurfaceVariant = CurfewStoneDark,
)

private val LightColorScheme = lightColorScheme(
    primary = CurfewAmber,
    onPrimary = Color.White,
    primaryContainer = CurfewAmberContainer,
    onPrimaryContainer = Color(0xFF251A00),
    background = CurfewPaper,
    onBackground = CurfewInk,
    surface = CurfewPaper,
    onSurface = CurfewInk,
    onSurfaceVariant = CurfewStone,
    error = CurfewError,
)

@Composable
fun CurfewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
