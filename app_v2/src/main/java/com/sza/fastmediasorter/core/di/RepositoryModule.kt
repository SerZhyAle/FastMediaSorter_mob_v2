package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.data.repository.NetworkCredentialsRepositoryImpl
import com.sza.fastmediasorter.data.repository.PlaybackPositionRepositoryImpl
import com.sza.fastmediasorter.data.repository.ResourceRepositoryImpl
import com.sza.fastmediasorter.data.repository.SettingsRepositoryImpl
import com.sza.fastmediasorter.data.repository.FavoritesRepositoryImpl
import com.sza.fastmediasorter.data.repository.ThumbnailCacheRepositoryImpl
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindResourceRepository(
        impl: ResourceRepositoryImpl
    ): ResourceRepository
    
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
    
    @Binds
    @Singleton
    abstract fun bindNetworkCredentialsRepository(
        impl: NetworkCredentialsRepositoryImpl
    ): NetworkCredentialsRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(
        impl: FavoritesRepositoryImpl
    ): FavoritesRepository
    
    @Binds
    @Singleton
    abstract fun bindPlaybackPositionRepository(
        impl: PlaybackPositionRepositoryImpl
    ): PlaybackPositionRepository
    
    @Binds
    @Singleton
    abstract fun bindThumbnailCacheRepository(
        impl: ThumbnailCacheRepositoryImpl
    ): ThumbnailCacheRepository

    @Binds
    @Singleton
    abstract fun bindMediaStoreRepository(
        impl: com.sza.fastmediasorter.data.repository.MediaStoreRepositoryImpl
    ): com.sza.fastmediasorter.domain.repository.MediaStoreRepository
}
