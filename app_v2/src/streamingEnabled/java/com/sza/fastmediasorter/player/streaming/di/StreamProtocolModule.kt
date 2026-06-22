package com.sza.fastmediasorter.player.streaming.di

import androidx.media3.common.util.UnstableApi
import com.sza.fastmediasorter.domain.player.StreamProtocolSupport
import com.sza.fastmediasorter.player.streaming.FullStreamProtocolSupport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S0565: binds the full HLS/DASH/RTSP [StreamProtocolSupport] for streaming-capable flavors
 * (standard / legacy / noLegal / vr). Same package + name as the `streamingDisabled` variant;
 * exactly one compiles per build variant via Gradle source-set selection.
 */
@Module
@InstallIn(SingletonComponent::class)
object StreamProtocolModule {

    @Provides
    @Singleton
    @UnstableApi
    fun provideStreamProtocolSupport(impl: FullStreamProtocolSupport): StreamProtocolSupport = impl
}
