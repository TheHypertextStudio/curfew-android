package studio.hypertext.curfew.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestSecurityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun privilegedComponentsArePrivateOrSignaturePermissionProtected() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(
                (PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES).toLong(),
            ),
        )

        packageInfo.receivers.orEmpty()
            .forEach { receiver ->
                if (receiver.name.startsWith(context.packageName)) {
                    assertFalse("${receiver.name} must not be exported", receiver.exported)
                } else if (receiver.exported) {
                    assertSignaturePermission(receiver.name, receiver.permission)
                }
            }
        packageInfo.services.orEmpty()
            .forEach { service ->
                if (service.name.startsWith(context.packageName)) {
                    assertFalse("${service.name} must not be exported", service.exported)
                } else if (service.exported) {
                    assertSignaturePermission(service.name, service.permission)
                }
            }
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

    private fun assertSignaturePermission(componentName: String, permissionName: String?) {
        assertNotNull("$componentName must require a permission", permissionName)
        val permission = try {
            context.packageManager.getPermissionInfo(requireNotNull(permissionName), 0)
        } catch (_: PackageManager.NameNotFoundException) {
            // An undefined required permission grants access to no caller. This
            // is how Firebase stays closed on an AOSP device without GMS.
            return
        }
        assertEquals(
            "$componentName must require a signature permission",
            PermissionInfo.PROTECTION_SIGNATURE,
            permission.protection,
        )
    }
}
