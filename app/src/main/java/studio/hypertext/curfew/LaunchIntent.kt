package studio.hypertext.curfew

import android.content.Intent
import studio.hypertext.curfew.enforcement.CurfewAccessibilityService
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway

/**
 * Reads Android extras and data once, then delegates all selection and validation to the JVM
 * parser. The rest of the app receives typed values rather than an Intent.
 */
internal fun Intent.toLaunchRequest(): LaunchRequest = parseLaunchRequest(
    data = dataString,
    action = action,
    showAlarm = getBooleanExtra(MainActivity.EXTRA_SHOW_ALARM, false),
    campaignId = getStringExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID),
    interceptedPackage = getStringExtra(CurfewAccessibilityService.EXTRA_INTERCEPTED_PACKAGE),
)
