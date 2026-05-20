package com.sza.fastmediasorter.identity.di

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.identity.CredentialManagerGoogleIdentityRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt bindings for the real Google identity domain (strategic S0200).
 *
 * Mounted into every cloud-enabled flavor via the `cloudEnabled` source set declared in
 * `app_v2/build.gradle.kts`. The paired `cloudDisabled` source set ships [com.sza.fastmediasorter.identity.di.NoOpIdentityModule]
 * binding the no-op for the `lite` flavor - AGP mounts exactly one of the two per flavor, so
 * there is no duplicate-binding conflict.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IdentityModule {

    @Binds
    @Singleton
    abstract fun bindGoogleIdentityRepository(
        impl: CredentialManagerGoogleIdentityRepository
    ): GoogleIdentityRepository

    companion object {
        @Provides
        @Singleton
        @Named("googleWebClientId")
        fun provideWebClientId(@ApplicationContext context: Context): String =
            context.getString(R.string.google_web_client_id)
    }
}
