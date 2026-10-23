package studio.hypertext.curfew.checkin

import android.content.Context
import studio.hypertext.curfew.callback.CallbackConfigurationRepository

data class CheckInDescription(val callbackId: String, val displayLabel: String, val actionUrl: String?, val placeCode: Boolean, val available: Boolean = true)

class CheckInCatalog(context: Context) {
    private val places = PlaceCheckInRepository(context)
    private val services = CallbackConfigurationRepository(context)
    suspend fun find(id: String): CheckInDescription? {
        try {
            places.find(id)?.let { return CheckInDescription(it.id, it.label, null, true) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { return CheckInDescription(id, "Code unavailable", null, true, false) }
        return services.find(id)?.let { CheckInDescription(it.callbackId, it.displayLabel, it.actionUrl, false) }
    }
}
