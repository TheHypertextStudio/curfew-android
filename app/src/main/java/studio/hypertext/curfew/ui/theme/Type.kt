package studio.hypertext.curfew.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import studio.hypertext.curfew.R

private val display = FontFamily(Font(R.font.calistoga_regular))
private val body = FontFamily(Font(R.font.hanken_grotesk))

private val baseline = Typography(fontFamily = body)

val Typography = baseline.copy(
    displayLarge = baseline.displayLarge.copy(fontFamily = display),
    displayMedium = baseline.displayMedium.copy(fontFamily = display),
    displaySmall = baseline.displaySmall.copy(fontFamily = display),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = display),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = display),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = display),
    titleLarge = baseline.titleLarge.copy(fontFamily = body),
    titleMedium = baseline.titleMedium.copy(fontFamily = body),
    titleSmall = baseline.titleSmall.copy(fontFamily = body),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = body),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = body),
    bodySmall = baseline.bodySmall.copy(fontFamily = body),
    labelLarge = baseline.labelLarge.copy(fontFamily = body),
    labelMedium = baseline.labelMedium.copy(fontFamily = body),
    labelSmall = baseline.labelSmall.copy(fontFamily = body),
    displayLargeEmphasized = baseline.displayLargeEmphasized.copy(fontFamily = display),
    displayMediumEmphasized = baseline.displayMediumEmphasized.copy(fontFamily = display),
    displaySmallEmphasized = baseline.displaySmallEmphasized.copy(fontFamily = display),
    headlineLargeEmphasized = baseline.headlineLargeEmphasized.copy(fontFamily = display),
    headlineMediumEmphasized = baseline.headlineMediumEmphasized.copy(fontFamily = display),
    headlineSmallEmphasized = baseline.headlineSmallEmphasized.copy(fontFamily = display),
    titleLargeEmphasized = baseline.titleLargeEmphasized.copy(fontFamily = body),
    titleMediumEmphasized = baseline.titleMediumEmphasized.copy(fontFamily = body),
    titleSmallEmphasized = baseline.titleSmallEmphasized.copy(fontFamily = body),
    bodyLargeEmphasized = baseline.bodyLargeEmphasized.copy(fontFamily = body),
    bodyMediumEmphasized = baseline.bodyMediumEmphasized.copy(fontFamily = body),
    bodySmallEmphasized = baseline.bodySmallEmphasized.copy(fontFamily = body),
    labelLargeEmphasized = baseline.labelLargeEmphasized.copy(fontFamily = body),
    labelMediumEmphasized = baseline.labelMediumEmphasized.copy(fontFamily = body),
    labelSmallEmphasized = baseline.labelSmallEmphasized.copy(fontFamily = body),
)
