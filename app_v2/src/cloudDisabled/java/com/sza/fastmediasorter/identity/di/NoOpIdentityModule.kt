package com.sza.fastmediasorter.identity.di

import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInStore
import com.sza.fastmediasorter.identity.NoOpGoogleIdentityRepository
import com.sza.fastmediasorter.identity.NoOpTransferableSignInStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for the `cloudDisabled` source set (lite flavor only).
 *
 * Resolves [GoogleIdentityRepository] to the inert [NoOpGoogleIdentityRepository]. The paired
 * `cloudEnabled` source set declares its own `IdentityModule` binding the real Credential Manager
 * implementation - AGP mounts exactly one of the two per flavor (see `app_v2/build.gradle.kts`
 * `sourceSets` block) so there is no duplicate-binding conflict.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoOpIdentityModule {

    @Binds
    @Singleton
    abstract fun bindGoogleIdentityRepository(
        impl: NoOpGoogleIdentityRepository
    ): GoogleIdentityRepository

    @Binds
    @Singleton
    abstract fun bindTransferableSignInStore(
        impl: NoOpTransferableSignInStore
    ): TransferableSignInStore
}
