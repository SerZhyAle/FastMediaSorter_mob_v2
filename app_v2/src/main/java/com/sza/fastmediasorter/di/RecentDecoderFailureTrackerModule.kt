package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.playback.RecentDecoderFailureTracker
import com.sza.fastmediasorter.core.playback.RecentDecoderFailureTrackerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for [RecentDecoderFailureTracker] (S0213 Pillar A).
 *
 * Process-scoped singleton; the implementation keeps an in-memory map of recently failed
 * playback paths and is consulted by `PlayerMediaLoaderManager` before a new playback request.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecentDecoderFailureTrackerModule {

    @Binds
    @Singleton
    abstract fun bindRecentDecoderFailureTracker(
        impl: RecentDecoderFailureTrackerImpl,
    ): RecentDecoderFailureTracker
}
