package studio.hypertext.curfew.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
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

    fun prepareAlarmNotifications() = PerpetualAlarmService.ensureChannels(activity)

    fun openAccessibilitySettings() = open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openSecuritySettings() = open(Intent(Settings.ACTION_SECURITY_SETTINGS))

    fun openAccountPortal() = open(Intent(Intent.ACTION_VIEW, Uri.parse(ProductionServices.ACCOUNT_PORTAL)))

    fun openCheckIn(url: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(url)), R.string.check_in_link_failed)

    suspend fun enrollAccount() {
        BrowserOAuthEnrollment(activity).begin()
    }

    private fun open(intent: Intent, @StringRes failure: Int = R.string.system_destination_unavailable) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showUnavailable(failure)
        } catch (_: SecurityException) {
            showUnavailable(failure)
        }
    }

    private fun showUnavailable(@StringRes message: Int) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
