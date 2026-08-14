package studio.hypertext.curfew.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestSecurityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun privilegedComponentsAreExplicitlyNonExported() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(
                (PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES).toLong(),
            ),
        )

        packageInfo.receivers.orEmpty()
            .filterNot { it.name.endsWith("MainActivity") }
            .forEach { assertFalse("${it.name} must not be exported", it.exported) }
        packageInfo.services.orEmpty()
            .forEach { assertFalse("${it.name} must not be exported", it.exported) }
    }

    @Test
    fun alarmAndForegroundServicePermissionsAreDeclared() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue(permissions.contains(Manifest.permission.SCHEDULE_EXACT_ALARM))
        assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK))
        assertTrue(permissions.contains(Manifest.permission.INTERNET))
    }
}
