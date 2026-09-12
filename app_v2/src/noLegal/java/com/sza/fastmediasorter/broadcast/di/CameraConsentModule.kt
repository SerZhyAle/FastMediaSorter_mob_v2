package com.sza.fastmediasorter.broadcast.di

import com.sza.fastmediasorter.broadcast.CameraSessionConsentPolicy
import com.sza.fastmediasorter.broadcast.StandbyCameraSessionConsentPolicy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S2551: `noLegal` answers the watch's camera request from a standing arrangement, not from a prompt.
 *
 * Same class name and same package as the `standard` module beside it: the two sets are never mounted
 * together, so the flavor difference is which file exists rather than a flag either of them reads
 * (Rule 14).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CameraConsentModule {

    @Binds
    @Singleton
    abstract fun bindCameraSessionConsentPolicy(
        impl: StandbyCameraSessionConsentPolicy
    ): CameraSessionConsentPolicy
}
