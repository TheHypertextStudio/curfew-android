package studio.hypertext.curfew.security

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the production secure storage implementation behind the [SecretStore] interface.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecretStore(
        @ApplicationContext context: Context,
    ): SecretStore = AndroidKeystoreSecretStore(context)
}
