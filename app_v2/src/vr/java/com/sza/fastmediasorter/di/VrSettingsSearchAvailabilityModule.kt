package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.settings.search.SupportedMediaSection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * VR flavor: mirrors standard's full media surface.
 */
@Module
@InstallIn(SingletonComponent::class)
object VrSettingsSearchAvailabilityModule {

    @Provides
    @IntoSet
    @SupportedMediaSection
    fun images(): String = "images"

    @Provides
    @IntoSet
    @SupportedMediaSection
    fun video(): String = "video"

    @Provides
    @IntoSet
    @SupportedMediaSection
    fun audio(): String = "audio"

    @Provides
    @IntoSet
    @SupportedMediaSection
    fun documents(): String = "documents"
}
