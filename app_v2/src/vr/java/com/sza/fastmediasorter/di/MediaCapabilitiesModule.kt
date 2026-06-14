package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * vr flavor capability binding. Reads this flavor's BuildConfig (allowed in
 * a flavor source set) and exposes it as the shared [MediaCapabilities] surface so
 * src/main code never touches BuildConfig.SUPPORT_* directly.
 *
 * Also serves the noLegal flavor: noLegal mounts `src/vr/java` (see build.gradle.kts
 * sourceSets), so this module is compiled into noLegal builds too and reads noLegal's
 * BuildConfig there (BuildConfig is per-variant). noLegal therefore has no module of
 * its own - adding one would redeclare this object and double-bind MediaCapabilities.
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
        supportsDefaultPlayer = BuildConfig.SUPPORTS_DEFAULT_PLAYER,
    )
}
