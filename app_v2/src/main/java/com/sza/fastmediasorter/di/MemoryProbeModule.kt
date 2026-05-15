package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.memory.MemoryProbe
import com.sza.fastmediasorter.core.memory.MemoryProbeImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for the [MemoryProbe] observability channel introduced by S0207
 * Phase 01. The default implementation is process-wide and stateless; flavor
 * overrides may replace this binding via a flavor-specific module if a
 * different sink is required.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryProbeModule {

    @Binds
    @Singleton
    abstract fun bindMemoryProbe(impl: MemoryProbeImpl): MemoryProbe
}
