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
import studio.hypertext.curfew.checkin.CheckInCatalog
import studio.hypertext.curfew.checkin.PlaceCheckInRepository
import studio.hypertext.curfew.persistence.CallbackDefinitionDao
import studio.hypertext.curfew.security.SecretStore
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
    fun provideCallbackConfigurationRepository(
        dao: CallbackDefinitionDao,
        secrets: SecretStore,
    ): CallbackConfigurationRepository = CallbackConfigurationRepository(dao, secrets)

    @Provides
    @Singleton
    fun provideCheckInCatalog(
        places: PlaceCheckInRepository,
        services: CallbackConfigurationRepository,
    ): CheckInCatalog = CheckInCatalog(places, services)

    @Provides
    @Singleton
    fun provideWakeConditionSetup(
        @ApplicationContext context: Context,
    ): AndroidWakeConditionSetup = AndroidWakeConditionSetup(context)

    @Provides
    @Singleton
    fun providePlaceCheckInRepository(
        secrets: SecretStore,
        preferences: studio.hypertext.curfew.persistence.CurfewPreferences,
        cameraChecker: CameraAvailabilityChecker,
        @ApplicationContext context: Context,
    ): PlaceCheckInRepository = PlaceCheckInRepository(secrets, preferences, cameraChecker, context)
}
