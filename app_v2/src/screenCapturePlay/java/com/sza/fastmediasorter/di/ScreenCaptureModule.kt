package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.screencapture.ScreenGestureOverlayControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * S0418. Play-flavor (standard/photos) binding: contributes the MediaProjection-only controller into
 * the multibound capability set consumed by src/main. noLegal has its own identically-named module
 * binding the a11y-aware impl; the two source sets never compile together, so there is no clash.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenCaptureModule {

    @Binds
    @IntoSet
    abstract fun bindController(
        impl: ScreenGestureOverlayControllerImpl
    ): ScreenGestureOverlayController
}
