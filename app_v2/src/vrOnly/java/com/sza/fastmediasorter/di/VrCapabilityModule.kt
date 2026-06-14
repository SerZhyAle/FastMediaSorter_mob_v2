package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.CompiledCapabilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Contributes the VR capability id into `@CompiledCapabilities`. Mounted only by VR-capable builds
 * (vr/noLegal via `src/vrOnly`); vrStub flavors (standard/lite/photos/legacy) never add this id, so
 * `CapabilityAvailability.isVrAvailable()` is false there. Compile-time presence only - runtime XR
 * detection stays in the XR detection facade.
 */
@Module
@InstallIn(SingletonComponent::class)
object VrCapabilityModule {

    @Provides
    @IntoSet
    @CompiledCapabilities
    fun provideVr(): String = CapabilityAvailability.CAP_VR
}
