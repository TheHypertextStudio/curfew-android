package studio.hypertext.curfew.platform

import android.content.Context
import kotlinx.coroutines.Dispatchers
import studio.hypertext.curfew.callback.callbackPollScheduler
import studio.hypertext.curfew.checkin.PlaceCheckInRepository
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore
import studio.hypertext.curfew.sync.AccountPlaceCheckInPublisher

/**
 * Wires the repository for Android entry points and fixtures that construct
 * their own dependencies. Hilt consumers inject the repository or one of its
 * narrower interfaces instead.
 */
internal fun createAndroidPlaceCheckInRepository(context: Context): PlaceCheckInRepository {
    val appContext = context.applicationContext
    return PlaceCheckInRepository(
        secrets = AndroidKeystoreSecretStore(appContext),
        preferences = CurfewPreferences(appContext),
        cameraChecker = AndroidCameraAvailabilityChecker(appContext),
        runtime = AlarmRuntimeRepository(appContext),
        alarms = SunriseScheduler(appContext, appContext.callbackPollScheduler()),
        publisher = AccountPlaceCheckInPublisher(appContext),
        ioDispatcher = Dispatchers.IO,
    )
}
