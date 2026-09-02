package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.domain.launcher.ConfiguredWidgetInstanceCleaner
import com.sza.fastmediasorter.ui.launcher.gadget.ConfiguredWidgetInstanceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S2217: binds the instance-cleanup seam to its launcher implementation. The reset use case lives
 * in `src/main` and injects the interface, so every flavor must supply a binding - the ones that
 * mount this source set get this one, the rest bind a no-op of their own.
 */
@Module
@InstallIn(SingletonComponent::class)
object ConfiguredWidgetInstanceModule {

    @Provides
    @Singleton
    fun provideConfiguredWidgetInstanceCleaner(
        manager: ConfiguredWidgetInstanceManager,
    ): ConfiguredWidgetInstanceCleaner = manager
}
