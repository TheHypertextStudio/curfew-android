package studio.hypertext.curfew.ui

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class ThemeAwarenessInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deviceAppearanceChangesUpdateTheSharedThemeWithoutResettingContent() {
        val original = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        val config = mutableStateOf(Configuration(original).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        })
        var surface = Color.Unspecified
        var accent = Color.Unspecified
        var initializations = 0
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides config.value) {
                CurfewTheme {
                    remember { initializations++ }
                    val colors = MaterialTheme.colorScheme
                    SideEffect { surface = colors.surface; accent = colors.primary }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(Color(0xFFFBF7EF), surface)
            assertEquals(Color(0xFFA83B1B), accent)
            config.value = Configuration(original).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
        }
        compose.runOnIdle {
            assertEquals(Color(0xFF1E1A20), surface)
            assertEquals(Color(0xFFFFB58E), accent)
            assertEquals(1, initializations)
        }
    }
}
