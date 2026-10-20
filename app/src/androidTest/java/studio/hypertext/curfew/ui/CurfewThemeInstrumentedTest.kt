package studio.hypertext.curfew.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class CurfewThemeInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun lightAppearanceUsesCurfewsBrandByDefault() {
        lateinit var colors: ColorScheme
        compose.setContent { CurfewTheme(darkTheme = false) { colors = MaterialTheme.colorScheme } }
        compose.waitForIdle()
            assertEquals(Color(0xFFA83B1B), colors.primary)
            assertEquals(Color(0xFFFBF7EF), colors.surface)
            assertEquals(Color(0xFFF4EEE4), colors.surfaceContainerLow)
            assertEquals(Color(0xFF29221F), colors.onSurface)
    }

    @Test
    fun darkAppearanceUsesCurfewsDarkRoles() {
        lateinit var colors: ColorScheme
        compose.setContent { CurfewTheme(darkTheme = true) { colors = MaterialTheme.colorScheme } }
        compose.waitForIdle()
            assertEquals(Color(0xFFFFB58E), colors.primary)
            assertEquals(Color(0xFF1E1A20), colors.surface)
            assertEquals(Color(0xFF252129), colors.surfaceContainerLow)
            assertEquals(Color(0xFFF6EEE3), colors.onSurface)
    }
}
