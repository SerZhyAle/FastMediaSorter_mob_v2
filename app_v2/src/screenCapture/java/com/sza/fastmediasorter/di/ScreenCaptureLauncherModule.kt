package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.screencapture.MenuScreenshotLauncherImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenCaptureLauncherModule {

    @Binds
    @IntoSet
    abstract fun bindMenuScreenshotLauncher(
        impl: MenuScreenshotLauncherImpl
    ): MenuScreenshotLauncher
}
