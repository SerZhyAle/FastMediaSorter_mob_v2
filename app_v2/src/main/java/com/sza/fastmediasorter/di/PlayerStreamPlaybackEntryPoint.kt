package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.usecase.streams.ResolvePlayerStreamUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** VideoPlayerManager is constructed by its host, so stream resolution enters the graph at playback time. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerStreamPlaybackEntryPoint {
    fun resolvePlayerStreamUseCase(): ResolvePlayerStreamUseCase
}
