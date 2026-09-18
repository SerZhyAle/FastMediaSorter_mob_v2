package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.CompiledCapabilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Contributes the cloud capability id into `@CompiledCapabilities`. Mounted only by cloud-capable
 * flavors (standard/noLegal/legacy/vr/photos via `src/cloudEnabled`); lite/foss mount
 * `cloudDisabled` and therefore never add this id, so `isCloudAvailable()` is false there.
 *
 * S1565: the multibound set is how this question has to arrive. Reading the cloud build flag
 * directly inside `CapabilityAvailability` was refused by the `flavor-flags` dimension of
 * `scripts/quality/assert-source-gates.ps1`, whose baseline only ratchets down.
 */
@Module
@InstallIn(SingletonComponent::class)
object CloudCapabilityModule {

    @Provides
    @IntoSet
    @CompiledCapabilities
    fun provideCloud(): String = CapabilityAvailability.CAP_CLOUD
}
