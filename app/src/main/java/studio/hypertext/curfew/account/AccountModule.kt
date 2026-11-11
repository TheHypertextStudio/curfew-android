package studio.hypertext.curfew.account

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    fun provideOAuthTokenExchange(
        @ApplicationContext context: Context,
    ): OAuthTokenExchange = OAuthTokenExchange(context)
}
