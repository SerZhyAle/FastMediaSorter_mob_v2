package com.sza.fastmediasorter.broadcast.di

import com.sza.fastmediasorter.broadcast.CameraSessionConsentNotifier
import com.sza.fastmediasorter.broadcast.CameraSessionConsentPolicy
import com.sza.fastmediasorter.broadcast.CameraSessionConsentPrompt
import com.sza.fastmediasorter.broadcast.NotificationCameraSessionConsentPolicy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S2551: `standard` answers the watch's camera request by asking the owner.
 *
 * One module per flavor source set rather than a flag inside a shared one, which is Rule 14 and the
 * pattern `BroadcastSourceModule` beside it already uses.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CameraConsentModule {

    @Binds
    @Singleton
    abstract fun bindCameraSessionConsentPolicy(
        impl: NotificationCameraSessionConsentPolicy
    ): CameraSessionConsentPolicy

    @Binds
    @Singleton
    abstract fun bindCameraSessionConsentPrompt(
        impl: CameraSessionConsentNotifier
    ): CameraSessionConsentPrompt
}
