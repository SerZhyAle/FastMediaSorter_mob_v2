package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.CompiledCapabilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Contributes the OCR capability id into `@CompiledCapabilities`. Mounted only by OCR-capable
 * flavors (standard/noLegal/legacy/vr via `src/ocrEnabled`); lite/photos mount `ocrDisabled` and
 * therefore never add this id, so `CapabilityAvailability.isOcrCompiledIn()` is false there.
 */
@Module
@InstallIn(SingletonComponent::class)
object OcrCapabilityModule {

    @Provides
    @IntoSet
    @CompiledCapabilities
    fun provideOcr(): String = CapabilityAvailability.CAP_OCR
}
