package studio.hypertext.curfew.ui

import android.content.ComponentName
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import studio.hypertext.curfew.R
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import studio.hypertext.curfew.MainActivity

class ModernAppShellInstrumentedTest {
    @Test
    fun launcherUsesTheSystemSplashTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals("Theme.Curfew.Starting", context.resources.getResourceEntryName(info.themeResource))
    }
    @Test
    fun nativeThemeFollowsTheDeviceLightAndDarkMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((mode, expectedLight) in listOf(Configuration.UI_MODE_NIGHT_NO to true, Configuration.UI_MODE_NIGHT_YES to false)) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_Curfew)
            val attributes = themed.obtainStyledAttributes(intArrayOf(android.R.attr.isLightTheme))
            try { assertEquals("Native windows follow device night mode", expectedLight, attributes.getBoolean(0, true)) }
            finally { attributes.recycle() }
        }
    }
}
