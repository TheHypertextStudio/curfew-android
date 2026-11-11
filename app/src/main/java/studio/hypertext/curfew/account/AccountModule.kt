package studio.hypertext.curfew.account

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the account recovery adapter and the token exchange port in the application component.
 * These implementations use application context and do not retain a screen.
 */
@Module
@InstallIn(SingletonComponent::class)
object AccountModule {

    @Provides
    @Singleton
    fun provideAccountRecovery(
        @ApplicationContext context: Context,
    ): AndroidAccountRecovery = AndroidAccountRecovery(context)

    @Provides
    @Singleton
    internal fun provideAccountSignIn(
        @ApplicationContext context: Context,
    ): AccountSignIn = AccountSignIn(OAuthTokenExchange(context)::exchange)
}
