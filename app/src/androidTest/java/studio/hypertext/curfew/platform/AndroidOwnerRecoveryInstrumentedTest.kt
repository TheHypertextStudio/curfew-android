package studio.hypertext.curfew.platform

import android.app.Activity
import android.app.KeyguardManager
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

class AndroidOwnerRecoveryInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun missingScreenLockCannotAuthorizeRecovery() {
        lateinit var activity: Activity
        compose.setContent { activity = requireNotNull(LocalActivity.current) }
        compose.waitForIdle()
        assumeFalse(activity.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        var elapsed = 1_000L
        var completed = false
        compose.runOnUiThread {
            val recovery = AndroidOwnerRecovery(
                activity = activity as androidx.activity.ComponentActivity,
                onRecovered = { completed = true },
                elapsedMillis = { elapsed },
            )
            recovery.beginHold("fixture", "My morning check-in is unavailable and I need to leave for work.")
            elapsed += 3_000
            recovery.requestConfirmation()
            assertFalse(recovery.busy)
            assertTrue(recovery.error.orEmpty().contains("screen lock"))
            assertFalse(completed)
            recovery.close()
        }
    }

    @Test
    fun nativeCredentialPromptHasItsDeclaredPermission() {
        lateinit var activity: Activity
        compose.setContent { activity = requireNotNull(LocalActivity.current) }
        compose.waitForIdle()
        val info = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.USE_BIOMETRIC"))
    }
}
