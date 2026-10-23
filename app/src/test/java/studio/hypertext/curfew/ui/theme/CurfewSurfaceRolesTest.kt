package studio.hypertext.curfew.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class CurfewSurfaceRolesTest {
    @Test
    fun brightSurfacesHaveHigherLuminanceThanDimSurfacesInBothThemes() {
        for (colors in listOf(CurfewLightColorScheme, CurfewDarkColorScheme)) {
            assertTrue("surfaceBright must be lighter than surfaceDim",
                colors.surfaceBright.luminance() > colors.surfaceDim.luminance())
        }
    }
}
