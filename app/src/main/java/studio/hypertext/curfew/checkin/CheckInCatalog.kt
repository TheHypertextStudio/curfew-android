package studio.hypertext.curfew.checkin

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import studio.hypertext.curfew.callback.CallbackConfigurationRepository

data class CheckInDescription(val callbackId: String, val displayLabel: String, val actionUrl: String?, val placeCode: Boolean, val available: Boolean = true)

@Singleton
class CheckInCatalog @Inject constructor(
    private val places: PlaceCheckInRepository,
    private val services: CallbackConfigurationRepository,
) {
    constructor(context: Context) : this(
        places = PlaceCheckInRepository(context),
        services = CallbackConfigurationRepository(context),
    )

    suspend fun find(id: String): CheckInDescription? {
        try {
            places.find(id)?.let { return CheckInDescription(it.id, it.label, null, true) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { return CheckInDescription(id, "Code unavailable", null, true, false) }
        return services.find(id)?.let { CheckInDescription(it.callbackId, it.displayLabel, it.actionUrl, false) }
    }
}
