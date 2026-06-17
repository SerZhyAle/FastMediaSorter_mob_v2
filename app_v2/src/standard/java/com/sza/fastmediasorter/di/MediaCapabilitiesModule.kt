package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * standard flavor capability binding. Reads this flavor's BuildConfig (allowed in
 * a flavor source set) and exposes it as the shared [MediaCapabilities] surface so
 * src/main code never touches BuildConfig.SUPPORT_* directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaCapabilitiesModule {

    @Provides
    @Singleton
    fun provideMediaCapabilities(): MediaCapabilities = MediaCapabilities(
        supportsVideo = BuildConfig.SUPPORT_VIDEO,
        supportsAudio = BuildConfig.SUPPORT_AUDIO,
        supportsImages = BuildConfig.SUPPORT_IMAGES,
        supportsDocuments = BuildConfig.SUPPORT_DOCUMENTS,
        supportsEpub = BuildConfig.ENABLE_EPUB,
        supportsCloud = BuildConfig.SUPPORT_CLOUD,
        supportsLocalNetworkSources = BuildConfig.SUPPORT_LOCAL_NETWORK,
        supportsDefaultPlayer = BuildConfig.SUPPORTS_DEFAULT_PLAYER,
        supportsCast = BuildConfig.SUPPORT_CAST,
        supportsMicRecording = BuildConfig.SUPPORT_MIC_RECORDING,
        supportsVrPlayer = BuildConfig.SUPPORT_VR_PLAYER,
        supportsWearCompanion = BuildConfig.SUPPORT_WEAR_COMPANION,
    )
}
