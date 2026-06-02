package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.data.repository.NetworkCredentialsRepositoryImpl
import com.sza.fastmediasorter.data.game.GameStateRepositoryImpl
import com.sza.fastmediasorter.data.wear.WearableDataLayerRepositoryImpl
import com.sza.fastmediasorter.domain.game.GameStateRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.data.repository.ScheduledOperationRepositoryImpl
import com.sza.fastmediasorter.data.repository.PlaybackPositionRepositoryImpl
import com.sza.fastmediasorter.data.repository.ResourceRepositoryImpl
import com.sza.fastmediasorter.data.repository.ResumeStateRepositoryImpl
import com.sza.fastmediasorter.data.repository.SettingsRepositoryImpl
import com.sza.fastmediasorter.data.repository.FavoritesRepositoryImpl
import com.sza.fastmediasorter.data.repository.StreamingCacheRepositoryImpl
import com.sza.fastmediasorter.data.repository.ThumbnailCacheRepositoryImpl
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.data.repository.RealDeviceProfileRepository
import com.sza.fastmediasorter.domain.detector.DeviceProfileDetector
import com.sza.fastmediasorter.data.detector.RealDeviceProfileDetector
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    companion object {
        @Provides
        @Singleton
        fun provideGson(): Gson = Gson()
    }
    
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
    abstract fun bindGameStateRepository(
        impl: GameStateRepositoryImpl
    ): GameStateRepository
    
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

    @Binds
    @Singleton
    abstract fun bindResumeStateRepository(
        impl: ResumeStateRepositoryImpl
    ): ResumeStateRepository

    @Binds
    @Singleton
    abstract fun bindScheduledOperationRepository(
        impl: ScheduledOperationRepositoryImpl
    ): ScheduledOperationRepository

    @Binds
    @Singleton
    abstract fun bindWearableDataLayerRepository(
        impl: WearableDataLayerRepositoryImpl
    ): WearableDataLayerRepository

    @Binds
    @Singleton
    abstract fun bindStreamingCacheRepository(
        impl: StreamingCacheRepositoryImpl
    ): StreamingCacheRepository

    @Binds
    @Singleton
    abstract fun bindDeviceProfileRepository(
        impl: RealDeviceProfileRepository
    ): DeviceProfileRepository

    @Binds
    @Singleton
    abstract fun bindDeviceProfileDetector(
        impl: RealDeviceProfileDetector
    ): DeviceProfileDetector
}
