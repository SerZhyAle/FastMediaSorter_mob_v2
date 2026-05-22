package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.settings.search.SupportedMediaSection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Photos flavor: images only.
 */
@Module
@InstallIn(SingletonComponent::class)
object PhotosSettingsSearchAvailabilityModule {

    @Provides
    @IntoSet
    @SupportedMediaSection
    fun images(): String = "images"
}
