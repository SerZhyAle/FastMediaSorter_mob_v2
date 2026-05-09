package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.link.streaming.NoOpStreamingPipeline
import com.sza.fastmediasorter.domain.usecase.link.streaming.StreamingPipeline
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar I: no-op `StreamingPipeline` binding for `lite` and `photos`
 * flavors. Same package + class name as the `streamingEnabled` variant; only one
 * compiles per build variant (Gradle source-set selection per flavor).
 */
@Module
@InstallIn(SingletonComponent::class)
object StreamingModule {

    @Provides
    @Singleton
    fun provideStreamingPipeline(impl: NoOpStreamingPipeline): StreamingPipeline = impl
}
