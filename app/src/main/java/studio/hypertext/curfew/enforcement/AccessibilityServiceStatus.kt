package studio.hypertext.curfew.enforcement

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/**
 * Reads Android's enabled accessibility service list for Curfew. It reports platform
 * capability without enabling the service.
 */
object AccessibilityServiceStatus {
    fun isEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
    }
}
