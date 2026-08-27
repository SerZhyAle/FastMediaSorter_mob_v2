package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.settings.search.SupportedMediaSection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * foss flavor: images, video, audio and documents. Wider than lite, because what foss subtracts is
 * the proprietary layer, not a media family.
 */
// A Hilt @IntoSet contribution is a function by construction - the multibinding is keyed on the
// provider, not on the value - so a constant body is the intended shape here, not a missed constant.
// The five sibling modules carry the same four findings in the detekt baseline; suppressed at the
// source instead, so the baseline does not grow by four every time a flavor is added.
@Suppress("FunctionOnlyReturningConstant")
@Module
@InstallIn(SingletonComponent::class)
object FossSettingsSearchAvailabilityModule {

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
