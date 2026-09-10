package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.repository.youtube.YouTubeChannelRepositoryImpl
import com.sza.fastmediasorter.domain.repository.YouTubeChannelRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S2032: binds the keyless YouTube lookup.
 *
 * Lives in `src/main` although only the launcher flavors use it, because the gadget itself sits in the
 * `launcherEnabled` source set and reaches across that boundary through the interface, never through
 * the provider (strategic §5.3).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class YouTubeModule {

    @Binds
    @Singleton
    abstract fun bindYouTubeChannelRepository(impl: YouTubeChannelRepositoryImpl): YouTubeChannelRepository
}
