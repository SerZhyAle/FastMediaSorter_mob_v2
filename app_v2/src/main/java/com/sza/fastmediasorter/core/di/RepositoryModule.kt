package com.sza.fastmediasorter.core.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sza.fastmediasorter.core.serialization.InstantTypeAdapter
import com.sza.fastmediasorter.data.detector.RealDeviceProfileDetector
import com.sza.fastmediasorter.data.game.GameStateRepositoryImpl
import com.sza.fastmediasorter.data.repository.DischargeRateBatteryRuntimeEstimator
import com.sza.fastmediasorter.data.repository.FavoritesRepositoryImpl
import com.sza.fastmediasorter.data.repository.NetworkCredentialsRepositoryImpl
import com.sza.fastmediasorter.data.repository.PlatformDeviceMemorySource
import com.sza.fastmediasorter.data.repository.PlatformStorageVolumeSource
import com.sza.fastmediasorter.data.repository.PlaybackPositionRepositoryImpl
import com.sza.fastmediasorter.data.repository.RealDeviceProfileRepository
import com.sza.fastmediasorter.data.repository.ResourceRepositoryImpl
import com.sza.fastmediasorter.data.repository.ResumeStateRepositoryImpl
import com.sza.fastmediasorter.data.repository.ScheduledOperationRepositoryImpl
import com.sza.fastmediasorter.data.repository.SettingsRepositoryImpl
import com.sza.fastmediasorter.data.repository.StatisticsRepositoryImpl
import com.sza.fastmediasorter.data.repository.StorageVolumeRepositoryImpl
import com.sza.fastmediasorter.data.repository.StorageVolumeSource
import com.sza.fastmediasorter.data.repository.StreamingCacheRepositoryImpl
import com.sza.fastmediasorter.data.repository.ThumbnailCacheRepositoryImpl
import com.sza.fastmediasorter.data.repository.streams.RealStreamFrameIngestor
import com.sza.fastmediasorter.domain.detector.DeviceProfileDetector
import com.sza.fastmediasorter.domain.game.GameStateRepository
import com.sza.fastmediasorter.domain.repository.BatteryRuntimeEstimator
import com.sza.fastmediasorter.domain.repository.DeviceMemoryRepository
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.StatisticsRepository
import com.sza.fastmediasorter.domain.repository.StorageVolumeRepository
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import com.sza.fastmediasorter.domain.streams.StreamFrameIngestor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    companion object {
        // S1668: [InstantTypeAdapter] replaces the reflective walk over java.time.Instant's private fields that a
        // bare Gson() falls back to. That walk is refused under JPMS and is a non-SDK interface read on ART, where
        // failing silently costs the persisted boundAt of the primary account binding. The adapter emits the same
        // bytes, so blobs written by earlier builds keep parsing.
        @Provides
        @Singleton
        fun provideGson(): Gson = GsonBuilder()
            .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
            .create()
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
    abstract fun bindStatisticsRepository(
        impl: StatisticsRepositoryImpl
    ): StatisticsRepository

    @Binds
    @Singleton
    abstract fun bindStorageVolumeSource(
        impl: PlatformStorageVolumeSource
    ): StorageVolumeSource

    @Binds
    @Singleton
    abstract fun bindStorageVolumeRepository(
        impl: StorageVolumeRepositoryImpl
    ): StorageVolumeRepository

    @Binds
    @Singleton
    abstract fun bindDeviceMemoryRepository(
        impl: PlatformDeviceMemorySource
    ): DeviceMemoryRepository

    @Binds
    @Singleton
    abstract fun bindBatteryRuntimeEstimator(
        impl: DischargeRateBatteryRuntimeEstimator
    ): BatteryRuntimeEstimator

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
    abstract fun bindStreamResumeStateRepository(
        impl: com.sza.fastmediasorter.data.repository.StreamResumeStateRepositoryImpl
    ): com.sza.fastmediasorter.domain.repository.StreamResumeStateRepository

    @Binds
    @Singleton
    abstract fun bindScheduledOperationRepository(
        impl: ScheduledOperationRepositoryImpl
    ): ScheduledOperationRepository

    // WearableDataLayerRepository is bound per-flavor by the seam modules (S0403):
    // src/wearGms/.../di/WearModule (GMS impl) or src/wearStub/.../di/WearModule (no-op).

    @Binds
    @Singleton
    abstract fun bindStreamingCacheRepository(
        impl: StreamingCacheRepositoryImpl
    ): StreamingCacheRepository

    @Binds
    @Singleton
    // Keeps fullscreen and headless capture on one accepted-frame ingest contract.
    abstract fun bindStreamFrameIngestor(
        impl: RealStreamFrameIngestor
    ): StreamFrameIngestor

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
