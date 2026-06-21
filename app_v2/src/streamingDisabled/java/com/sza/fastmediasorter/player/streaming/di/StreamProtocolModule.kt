package com.sza.fastmediasorter.player.streaming.di

import com.sza.fastmediasorter.domain.player.StreamProtocolSupport
import com.sza.fastmediasorter.player.streaming.ProgressiveOnlyStreamProtocolSupport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S0565: binds the progressive-only [StreamProtocolSupport] for lite/photos, where the Media3
 * HLS/DASH/RTSP modules are absent. Same package + name as the `streamingEnabled` variant;
 * exactly one compiles per build variant via Gradle source-set selection.
 */
@Module
@InstallIn(SingletonComponent::class)
object StreamProtocolModule {

    @Provides
    @Singleton
    fun provideStreamProtocolSupport(impl: ProgressiveOnlyStreamProtocolSupport): StreamProtocolSupport = impl
}
