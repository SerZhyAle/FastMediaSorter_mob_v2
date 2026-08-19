package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.CompiledCapabilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Contributes the noLegal-exclusive capability ids into `@CompiledCapabilities`. Mounted only by the
 * noLegal flavor, so on every other flavor these ids are absent and the corresponding
 * `CapabilityAvailability` checks return false - the NewPipe license card stays hidden without any
 * `BuildConfig.IS_*` gate in shared code (CLAUDE.md Rule 15).
 */
@Module
@InstallIn(SingletonComponent::class)
object NoLegalCapabilityModule {

    @Provides
    @IntoSet
    @CompiledCapabilities
    fun provideNewPipe(): String = CapabilityAvailability.CAP_NEWPIPE
}
