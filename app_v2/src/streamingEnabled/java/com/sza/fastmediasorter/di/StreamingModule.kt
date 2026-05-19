package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.link.streaming.StreamingDownloadStrategy
import com.sza.fastmediasorter.domain.usecase.link.streaming.StreamingPipeline
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar I: real `StreamingPipeline` binding for video-supporting flavors
 * (standard, legacy, vr, noLegal). Compiled only when the `streamingEnabled`
 * shared source-set is active for the current build variant.
 */
@Module
@InstallIn(SingletonComponent::class)
object StreamingModule {

    @Provides
    @Singleton
    fun provideStreamingPipeline(impl: StreamingDownloadStrategy): StreamingPipeline = impl
}
