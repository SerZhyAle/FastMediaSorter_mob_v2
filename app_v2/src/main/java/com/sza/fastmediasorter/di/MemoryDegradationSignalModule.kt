package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.memory.MemoryDegradationSignal
import com.sza.fastmediasorter.core.memory.MemoryDegradationSignalImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for [MemoryDegradationSignal] (S0213 §5.1 Pillar C).
 *
 * The signal channel is process-scoped; the producer ([com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker])
 * and the consumer (`PlayerActivity`) both resolve the same singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryDegradationSignalModule {

    @Binds
    @Singleton
    abstract fun bindMemoryDegradationSignal(
        impl: MemoryDegradationSignalImpl,
    ): MemoryDegradationSignal
}
