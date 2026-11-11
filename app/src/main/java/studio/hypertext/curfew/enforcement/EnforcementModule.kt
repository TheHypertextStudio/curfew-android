package studio.hypertext.curfew.enforcement

import android.content.Context
import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EnforcementModule {

    @Provides
    @Singleton
    fun provideEnforcementPreferences(
        @ApplicationContext context: Context,
    ): EnforcementPreferences = EnforcementPreferences(context)

    @Provides
    @Singleton
    fun provideAccessibilityChecker(
        @ApplicationContext context: Context,
    ): AccessibilityChecker = AccessibilityChecker { AccessibilityServiceStatus.isEnabled(context) }

    @Provides
    internal fun provideProtectionRuntime(): ProtectionRuntime = object : ProtectionRuntime {
        override fun wallClockMillis() = System.currentTimeMillis()
        override fun elapsedRealtimeMillis() = SystemClock.elapsedRealtime()
        override fun disarm() = CurfewAccessibilityService.disarmActiveSession()
    }
}
