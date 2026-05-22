package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.settings.search.SupportedMediaSection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * noLegal flavor: mirrors standard's full media surface (images + video + audio + documents).
 */
@Module
@InstallIn(SingletonComponent::class)
object NoLegalSettingsSearchAvailabilityModule {

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
