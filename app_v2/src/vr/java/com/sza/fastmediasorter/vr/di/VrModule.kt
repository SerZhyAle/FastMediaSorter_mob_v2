package com.sza.fastmediasorter.vr.di

import com.sza.fastmediasorter.ui.player.commands.FullscreenCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SaveFrameCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SystemUiCommandOverride
import com.sza.fastmediasorter.vr.commands.VrFullscreenCommandOverride
import com.sza.fastmediasorter.vr.commands.VrSaveFrameCommandOverride
import com.sza.fastmediasorter.vr.commands.VrSystemUiCommandOverride
import com.sza.fastmediasorter.vr.playback.ExoVrPlaybackEngine
import com.sza.fastmediasorter.vr.playback.VrPlaybackEngine
import com.sza.fastmediasorter.vr.render.DefaultVrLayerFactory
import com.sza.fastmediasorter.vr.render.VrLayerFactory
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

    /** Layer selection stays injectable so future phases can swap heuristics without touching the host. */
    @Singleton
    @Binds
    abstract fun bindVrLayerFactory(impl: DefaultVrLayerFactory): VrLayerFactory

    @Singleton
    @Binds
    abstract fun bindVrFullscreenCommandOverride(impl: VrFullscreenCommandOverride): FullscreenCommandOverride

    @Singleton
    @Binds
    abstract fun bindVrSaveFrameCommandOverride(impl: VrSaveFrameCommandOverride): SaveFrameCommandOverride

    @Singleton
    @Binds
    abstract fun bindVrSystemUiCommandOverride(impl: VrSystemUiCommandOverride): SystemUiCommandOverride
}
