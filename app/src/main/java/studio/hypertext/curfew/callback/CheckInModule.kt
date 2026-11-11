package studio.hypertext.curfew.callback

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import studio.hypertext.curfew.checkin.CameraAvailabilityChecker
import studio.hypertext.curfew.checkin.PlaceCheckInApprover
import studio.hypertext.curfew.checkin.PlaceCheckInAlarmLifecycle
import studio.hypertext.curfew.checkin.PlaceCheckInPublisher
import studio.hypertext.curfew.checkin.PlaceCheckInRepository
import studio.hypertext.curfew.checkin.PlaceCheckInSetupAccess
import studio.hypertext.curfew.platform.AndroidCameraAvailabilityChecker
import studio.hypertext.curfew.platform.SunriseScheduler
import studio.hypertext.curfew.sync.AccountPlaceCheckInPublisher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CheckInModule {

    @Provides
    @Singleton
    fun provideCameraAvailabilityChecker(
        @ApplicationContext context: Context,
    ): CameraAvailabilityChecker = AndroidCameraAvailabilityChecker(context)

    @Provides
    @Singleton
    fun provideWakeConditionSetup(
        @ApplicationContext context: Context,
    ): AndroidWakeConditionSetup = AndroidWakeConditionSetup(context)

    @Provides
    fun providePlaceSetup(places: PlaceCheckInRepository): PlaceCheckInSetupAccess = places

    @Provides
    internal fun providePlaceApprover(places: PlaceCheckInRepository): PlaceCheckInApprover = PlaceCheckInApprover(places::approve)

    @Provides
    internal fun providePlaceAlarmLifecycle(scheduler: SunriseScheduler): PlaceCheckInAlarmLifecycle = scheduler

    @Provides
    internal fun providePlacePublisher(@ApplicationContext context: Context): PlaceCheckInPublisher = AccountPlaceCheckInPublisher(context)
}
