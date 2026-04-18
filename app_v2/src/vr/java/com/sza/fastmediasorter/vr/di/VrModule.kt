package com.sza.fastmediasorter.vr.di

import com.sza.fastmediasorter.vr.playback.ExoVrPlaybackEngine
import com.sza.fastmediasorter.vr.playback.VrPlaybackEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for vr flavor.
 * Binds ExoPlayer-based VrPlaybackEngine — no second media engine per spec decision.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VrModule {

    /** ExoPlayer backend for VR playback. Surface is set in prepare() after XR swapchain is ready. */
    @Singleton
    @Binds
    abstract fun bindVrPlaybackEngine(impl: ExoVrPlaybackEngine): VrPlaybackEngine
}
