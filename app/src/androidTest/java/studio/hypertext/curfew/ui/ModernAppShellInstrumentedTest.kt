package studio.hypertext.curfew.ui

import android.content.ComponentName
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
}
