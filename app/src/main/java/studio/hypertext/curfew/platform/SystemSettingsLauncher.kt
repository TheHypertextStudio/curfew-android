package studio.hypertext.curfew.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import studio.hypertext.curfew.R
import studio.hypertext.curfew.account.BrowserOAuthEnrollment
import studio.hypertext.curfew.config.ProductionServices

@ActivityScoped
internal class SystemSettingsLauncher @Inject constructor(private val activity: Activity) {
    private val packageUri get() = Uri.fromParts("package", activity.packageName, null)

    fun requestExactAlarmAccess() =
        open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))

    fun requestFullScreenAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))
        }
    }

    fun openAlarmChannel() = open(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, PerpetualAlarmService.ALARM_CHANNEL_ID),
    )

    fun openSoundSettings() = open(Intent(Settings.ACTION_SOUND_SETTINGS))

    fun openAccessibilitySettings() = open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAccountPortal() = open(Intent(Intent.ACTION_VIEW, Uri.parse(ProductionServices.ACCOUNT_PORTAL)))

    suspend fun enrollAccount() {
        BrowserOAuthEnrollment(activity).begin()
    }

    private fun open(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showUnavailable()
        } catch (_: SecurityException) {
            showUnavailable()
        }
    }

    private fun showUnavailable() {
        Toast.makeText(activity, R.string.system_destination_unavailable, Toast.LENGTH_LONG).show()
    }
}
