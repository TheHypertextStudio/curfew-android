package studio.hypertext.curfew.persistence

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the single Curfew Room database, its DAOs, and the preference adapters in the
 * application component.
 */
@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {

    @Provides
    @Singleton
    fun provideCurfewDatabase(
        @ApplicationContext context: Context,
    ): CurfewDatabase = CurfewDatabase.open(context)

    @Provides
    @Singleton
    fun provideAlarmCampaignDao(
        database: CurfewDatabase,
    ): AlarmCampaignDao = database.alarmCampaignDao()

    @Provides
    @Singleton
    fun provideSyncOutboxDao(
        database: CurfewDatabase,
    ): SyncOutboxDao = database.syncOutboxDao()

    @Provides
    @Singleton
    fun provideCallbackDefinitionDao(
        database: CurfewDatabase,
    ): CallbackDefinitionDao = database.callbackDefinitionDao()

    @Provides
    @Singleton
    fun provideCurfewPreferences(
        @ApplicationContext context: Context,
    ): CurfewPreferences = CurfewPreferences(context)

    @Provides
    @Singleton
    fun provideFirstUsePreferences(
        @ApplicationContext context: Context,
    ): FirstUsePreferences = FirstUsePreferences(context)
}
