package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.playback.AltPlaybackEngine
import com.sza.fastmediasorter.playback.VlcPlaybackEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * S1060: contributes the libVLC engine to the [AltPlaybackEngine] set for `noLegal` only. The
 * No-Op stays in the set (it never answers "can play"), so nothing in `src/main` changes and the
 * selector picks the real engine purely through [AltPlaybackEngine.canPlay].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalAltPlaybackModule {

    @Binds
    @IntoSet
    abstract fun bindVlcPlaybackEngine(impl: VlcPlaybackEngine): AltPlaybackEngine
}
