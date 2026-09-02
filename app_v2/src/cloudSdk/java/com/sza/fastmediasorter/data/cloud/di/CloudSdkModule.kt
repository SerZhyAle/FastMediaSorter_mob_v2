package com.sza.fastmediasorter.data.cloud.di

import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.DropboxClientImpl
import com.sza.fastmediasorter.data.cloud.GoogleDriveBrowserAuthManager
import com.sza.fastmediasorter.data.cloud.GoogleDriveBrowserAuthManagerImpl
import com.sza.fastmediasorter.data.cloud.GoogleDriveInteractiveSignInCoordinator
import com.sza.fastmediasorter.data.cloud.GoogleDriveInteractiveSignInCoordinatorImpl
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClientImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the `cloudSdk` source set (strategic S0403).
 *
 * Binds every cloud-provider contract to the implementation that links the proprietary SDK -
 * Dropbox, MSAL, AppAuth and Play Services auth. The paired `cloudNoSdk` module carries the same
 * four bindings against inert implementations; AGP mounts exactly one of the two source sets per
 * flavor. Keep this list in sync with the per-flavor dependency blocks in `app_v2/build.gradle.kts`
 * that scope those SDKs away from foss.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudSdkModule {

    @Binds
    @Singleton
    abstract fun bindDropboxClient(impl: DropboxClientImpl): DropboxClient

    @Binds
    @Singleton
    abstract fun bindOneDriveRestClient(impl: OneDriveRestClientImpl): OneDriveRestClient

    @Binds
    @Singleton
    abstract fun bindGoogleDriveBrowserAuthManager(
        impl: GoogleDriveBrowserAuthManagerImpl
    ): GoogleDriveBrowserAuthManager

    @Binds
    @Singleton
    abstract fun bindGoogleDriveInteractiveSignInCoordinator(
        impl: GoogleDriveInteractiveSignInCoordinatorImpl
    ): GoogleDriveInteractiveSignInCoordinator
}
