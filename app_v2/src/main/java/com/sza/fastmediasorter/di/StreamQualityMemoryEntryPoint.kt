package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.usecase.streams.GetStreamQualityMemoryUseCase
import com.sza.fastmediasorter.domain.usecase.streams.RecordStreamQualityMemoryUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S1511: exposes the stream quality memory to the stream playback helper, which hangs off
 * `VideoPlayerManager` - a class built by hand, outside the Hilt graph.
 *
 * The obvious alternative was the dependency bundle `VideoPlayerStoreDependencies`, the route S1144 took
 * for the per-channel track preference. It was written that way first and refused: the bundle is filled
 * from `PlayerActivity`, so every dependency added to it becomes another domain-layer field injection in
 * an Activity, which CLAUDE.md Rule 3 forbids and `assert-neuroslop`'s activity-logic dimension refuses
 * for anything new. The track preference predates that ratchet and is baselined; this does not.
 *
 * S2360: application coroutine scope resolution is consolidated into [ApplicationScopeEntryPoint] /
 * `Context.applicationScope()`, keeping this entry point dedicated to its own use cases.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface StreamQualityMemoryEntryPoint {
    fun getStreamQualityMemoryUseCase(): GetStreamQualityMemoryUseCase

    fun recordStreamQualityMemoryUseCase(): RecordStreamQualityMemoryUseCase
}
