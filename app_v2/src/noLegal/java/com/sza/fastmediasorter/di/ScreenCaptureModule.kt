package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.screencapture.ScreenGestureOverlayControllerImpl
import com.sza.fastmediasorter.widget.QuickRecorderIndicatorController
import com.sza.fastmediasorter.widget.QuickRecorderIndicatorControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenCaptureModule {

    @Binds
    @IntoSet
    abstract fun bindController(
        impl: ScreenGestureOverlayControllerImpl
    ): ScreenGestureOverlayController

    @Binds
    @IntoSet
    abstract fun bindQuickRecorderIndicatorController(
        impl: QuickRecorderIndicatorControllerImpl
    ): QuickRecorderIndicatorController
}
