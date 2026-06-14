package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.CompiledCapabilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Contributes the translation capability id into `@CompiledCapabilities`. Mounted only by
 * translation-capable flavors (standard/noLegal/legacy/vr via `src/translationEnabled`); lite/photos
 * mount no translation source set, so `CapabilityAvailability.isTranslationAvailable()` is false there.
 */
@Module
@InstallIn(SingletonComponent::class)
object TranslationCapabilityModule {

    @Provides
    @IntoSet
    @CompiledCapabilities
    fun provideTranslation(): String = CapabilityAvailability.CAP_TRANSLATION
}
