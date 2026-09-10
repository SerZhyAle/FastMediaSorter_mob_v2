package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.service.WatchListenPlayback
import com.sza.fastmediasorter.service.WatchListenStateRenderer
import com.sza.fastmediasorter.ui.player.helpers.Media3WatchListenPlayback
import com.sza.fastmediasorter.widget.RemoteWatchListenStateRenderer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** S2881: binds the listening session's playback port to the shipped Media3 path. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WatchListenModule {

    @Binds
    @Singleton
    abstract fun bindWatchListenPlayback(impl: Media3WatchListenPlayback): WatchListenPlayback

    /** The widget is the first surface the state push reaches; more renderers would multibind. */
    @Binds
    @Singleton
    abstract fun bindWatchListenStateRenderer(impl: RemoteWatchListenStateRenderer): WatchListenStateRenderer
}
