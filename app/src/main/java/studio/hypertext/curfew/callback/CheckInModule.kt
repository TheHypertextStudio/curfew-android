package studio.hypertext.curfew.callback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import studio.hypertext.curfew.checkin.CameraAvailabilityChecker
import studio.hypertext.curfew.checkin.PlaceCheckInApprover
import studio.hypertext.curfew.checkin.PlaceCheckInEffects
import studio.hypertext.curfew.checkin.PlaceCheckInRepository
import studio.hypertext.curfew.checkin.PlaceCheckInSetupAccess
import studio.hypertext.curfew.platform.AndroidPlaceCheckInEffects
import studio.hypertext.curfew.platform.SunriseScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CheckInModule {

    @Provides
    @Singleton
    fun provideCameraAvailabilityChecker(
        @ApplicationContext context: Context,
    ): CameraAvailabilityChecker = CameraAvailabilityChecker {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

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
    internal fun providePlaceEffects(
        @ApplicationContext context: Context,
        scheduler: SunriseScheduler,
    ): PlaceCheckInEffects = AndroidPlaceCheckInEffects(context, scheduler)
}
