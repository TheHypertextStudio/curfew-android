package studio.hypertext.curfew.alarm

import android.content.Context
import android.provider.Settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import studio.hypertext.curfew.persistence.AlarmCampaignDao
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmReadiness
import studio.hypertext.curfew.platform.AndroidAlarmSoundPreview
import studio.hypertext.curfew.platform.BootIdProvider
import studio.hypertext.curfew.platform.SunriseScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AlarmModule {

    @Provides
    @Singleton
    fun provideBootIdProvider(
        @ApplicationContext context: Context,
    ): BootIdProvider = BootIdProvider {
        "boot-${Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)}"
    }

    @Provides
    @Singleton
    fun provideSunriseScheduler(
        @ApplicationContext context: Context,
    ): SunriseScheduler = SunriseScheduler(context)

    @Provides
    @Singleton
    fun provideAlarmRuntimeRepository(
        database: CurfewDatabase,
        dao: AlarmCampaignDao,
        bootIdProvider: BootIdProvider,
    ): AlarmRuntimeRepository = AlarmRuntimeRepository(database, dao, bootIdProvider)

    @Provides
    @Singleton
    fun provideAlarmSoundPreview(
        @ApplicationContext context: Context,
    ): AndroidAlarmSoundPreview = AndroidAlarmSoundPreview(context)

    @Provides
    @Singleton
    fun provideAlarmReadiness(
        @ApplicationContext context: Context,
    ): AndroidAlarmReadiness = AndroidAlarmReadiness(context)
}
