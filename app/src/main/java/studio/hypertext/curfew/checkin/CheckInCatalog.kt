package studio.hypertext.curfew.checkin

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.platform.createAndroidPlaceCheckInRepository

/**
 * Display metadata for a saved verifier or place code. The callback ID identifies the binding;
 * [placeCode] selects the scanner rather than a URL, and [available] indicates whether it can
 * be used for new setup.
 */
data class CheckInDescription(val callbackId: String, val displayLabel: String, val actionUrl: String?, val placeCode: Boolean, val available: Boolean = true)

/**
 * Resolves a check-in ID to safe display metadata without exposing verifier or place-code
 * secrets. It checks place codes first and falls back to configured HTTPS verifiers.
 */
@Singleton
class CheckInCatalog @Inject constructor(
    private val places: PlaceCheckInRepository,
    private val services: CallbackConfigurationRepository,
) {
    constructor(context: Context) : this(
        places = createAndroidPlaceCheckInRepository(context),
        services = CallbackConfigurationRepository(context),
    )

    fun placeCameraReady(): Boolean = places.cameraReady()

    suspend fun find(id: String): CheckInDescription? {
        try {
            places.find(id)?.let { return CheckInDescription(it.id, it.label, null, true) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { return CheckInDescription(id, "Code unavailable", null, true, false) }
        return services.find(id)?.let { CheckInDescription(it.callbackId, it.displayLabel, it.actionUrl, false) }
    }
}
