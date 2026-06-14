package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenGestureOverlayModule {

    @Multibinds
    abstract fun controllers(): Set<ScreenGestureOverlayController>
}
