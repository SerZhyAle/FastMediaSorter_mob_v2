package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.settings.search.SupportedMediaSection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Lite flavor: images + video only. No audio, no documents.
 */
@Module
@InstallIn(SingletonComponent::class)
object LiteSettingsSearchAvailabilityModule {

    @Provides
    @IntoSet
    @SupportedMediaSection
    fun images(): String = "images"

    @Provides
    @IntoSet
    @SupportedMediaSection
    fun video(): String = "video"
}
