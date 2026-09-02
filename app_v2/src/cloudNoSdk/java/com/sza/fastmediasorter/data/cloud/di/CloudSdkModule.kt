package com.sza.fastmediasorter.data.cloud.di

import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveBrowserAuthManager
import com.sza.fastmediasorter.data.cloud.GoogleDriveInteractiveSignInCoordinator
import com.sza.fastmediasorter.data.cloud.NoOpDropboxClient
import com.sza.fastmediasorter.data.cloud.NoOpGoogleDriveBrowserAuthManager
import com.sza.fastmediasorter.data.cloud.NoOpGoogleDriveInteractiveSignInCoordinator
import com.sza.fastmediasorter.data.cloud.NoOpOneDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the `cloudNoSdk` source set (strategic S0403, FOSS flavor).
 *
 * Binds every cloud-provider contract to its inert implementation. The paired `cloudSdk` module
 * carries the same four bindings against the SDK-backed classes; AGP mounts exactly one of the two
 * source sets per flavor, so there is no duplicate-binding conflict.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudSdkModule {

    @Binds
    @Singleton
    abstract fun bindDropboxClient(impl: NoOpDropboxClient): DropboxClient

    @Binds
    @Singleton
    abstract fun bindOneDriveRestClient(impl: NoOpOneDriveRestClient): OneDriveRestClient

    @Binds
    @Singleton
    abstract fun bindGoogleDriveBrowserAuthManager(
        impl: NoOpGoogleDriveBrowserAuthManager
    ): GoogleDriveBrowserAuthManager

    @Binds
    @Singleton
    abstract fun bindGoogleDriveInteractiveSignInCoordinator(
        impl: NoOpGoogleDriveInteractiveSignInCoordinator
    ): GoogleDriveInteractiveSignInCoordinator
}
