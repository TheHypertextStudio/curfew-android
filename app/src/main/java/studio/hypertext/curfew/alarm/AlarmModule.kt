package studio.hypertext.curfew.alarm

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import studio.hypertext.curfew.platform.AndroidAlarmReadiness
import studio.hypertext.curfew.platform.AndroidAlarmSoundPreview
import studio.hypertext.curfew.platform.BootIdProvider
import studio.hypertext.curfew.platform.SunriseScheduler
import studio.hypertext.curfew.readiness.AlarmSoundOutput
import studio.hypertext.curfew.ui.NextAlarmTransition
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
    internal fun provideNextAlarmTransition(bootId: BootIdProvider): NextAlarmTransition = NextAlarmTransition { snapshot ->
        AlarmRuntimeClock.nextTransitionAt(snapshot, Instant.now(), SystemClock.elapsedRealtime(), bootId.currentBootId())
    }

    @Provides
    @Singleton
    fun provideSunriseScheduler(
        @ApplicationContext context: Context,
    ): SunriseScheduler = SunriseScheduler(context)

    @Provides
    internal fun provideAlarmSoundOutput(
        @ApplicationContext context: Context,
    ): AlarmSoundOutput {
        val preview = AndroidAlarmSoundPreview(context)
        return object : AlarmSoundOutput {
            override suspend fun play() = preview.play()
            override fun stop() = preview.stop()
        }
    }

    @Provides
    @Singleton
    internal fun provideAlarmReadiness(
        @ApplicationContext context: Context,
    ): AlarmReadinessReader = AlarmReadinessReader(AndroidAlarmReadiness(context)::evaluate)
}
