package studio.hypertext.curfew

import android.content.Intent
import studio.hypertext.curfew.enforcement.CurfewAccessibilityService
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway

internal fun Intent.toLaunchRequest(): LaunchRequest = parseLaunchRequest(
    data = dataString,
    action = action,
    showAlarm = getBooleanExtra(MainActivity.EXTRA_SHOW_ALARM, false),
    campaignId = getStringExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID),
    interceptedPackage = getStringExtra(CurfewAccessibilityService.EXTRA_INTERCEPTED_PACKAGE),
)
