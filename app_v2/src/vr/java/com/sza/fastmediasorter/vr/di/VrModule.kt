package com.sza.fastmediasorter.vr.di

import android.app.Application
import com.sza.fastmediasorter.ui.browse.managers.BrowsePassthroughCaptureProvider
import com.sza.fastmediasorter.ui.player.commands.FullscreenCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SaveFrameCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SystemUiCommandOverride
import com.sza.fastmediasorter.vr.capture.VrBrowsePassthroughCaptureManager
import com.sza.fastmediasorter.vr.commands.VrFullscreenCommandOverride
import com.sza.fastmediasorter.vr.commands.VrSaveFrameCommandOverride
import com.sza.fastmediasorter.vr.commands.VrSystemUiCommandOverride
import com.sza.fastmediasorter.vr.helpers.VrRecentDestinationsPrefs
import com.sza.fastmediasorter.ui.player.render.stereoscopic.DefaultVrLayerFactory
import com.sza.fastmediasorter.ui.player.render.stereoscopic.VrLayerFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for vr flavor.
 * Playback authority stays on the shared VideoPlayerManager / ExoPlayer path;
 * this module binds only VR-specific overrides and helpers.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VrModule {

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

    @Singleton
    @Binds
    abstract fun bindPassthroughCaptureProvider(
        impl: VrBrowsePassthroughCaptureManager,
    ): BrowsePassthroughCaptureProvider

    companion object {
        /**
         * Recent copy/move destinations for the VR immersive file-ops panel.
         * Per ADR-1 (spec_vr-immersive-controls-tech): SharedPreferences-backed,
         * no Room entity for this first iteration.
         */
        @Singleton
        @Provides
        fun provideVrRecentDestinationsPrefs(app: Application): VrRecentDestinationsPrefs {
            return VrRecentDestinationsPrefs(app)
        }
    }
}
