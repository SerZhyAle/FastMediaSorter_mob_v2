package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.capability.CompiledCapabilities
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the `@CompiledCapabilities Set<String>` multibinding so it injects even on flavors that
 * contribute nothing (lite/photos: ocrDisabled + vrStub + no translationEnabled). The actual ids
 * are contributed `@IntoSet` from the capability source sets `ocrEnabled`/`translationEnabled`/`vrOnly`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CapabilityAvailabilityModule {

    @Multibinds
    @CompiledCapabilities
    abstract fun compiledCapabilities(): Set<String>
}
