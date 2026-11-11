package studio.hypertext.curfew.di

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
    ): NotificationManager = context.getSystemService(NotificationManager::class.java)

    @Provides
    @Singleton
    fun provideAlarmManager(
        @ApplicationContext context: Context,
    ): AlarmManager = context.getSystemService(AlarmManager::class.java)

    @Provides
    @Singleton
    fun provideAudioManager(
        @ApplicationContext context: Context,
    ): AudioManager = context.getSystemService(AudioManager::class.java)

    @Provides
    @Singleton
    fun provideTelecomManager(
        @ApplicationContext context: Context,
    ): TelecomManager? = context.getSystemService(TelecomManager::class.java)

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
