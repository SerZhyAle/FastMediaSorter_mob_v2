package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.playback.AltPlaybackEngine
import com.sza.fastmediasorter.domain.playback.NoOpAltPlaybackEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * S1060: mirrors `OcrContributorModule` - engines arrive as a Hilt set multibinding so a flavor
 * source set contributes its implementation without editing `src/main` (Rule 14). The No-Op is
 * bound here so the set is never empty and every flavor satisfies the contract; `noLegal` adds
 * its real engine from its own module and the selector picks by [AltPlaybackEngine.canPlay].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AltPlaybackModule {

    @Binds
    @IntoSet
    abstract fun bindNoOpAltPlaybackEngine(impl: NoOpAltPlaybackEngine): AltPlaybackEngine
}
