package com.sza.fastmediasorter.ui.launcher.signal.di

import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalSource
import com.sza.fastmediasorter.ui.launcher.signal.source.BackgroundWorkLauncherSignalSource
import com.sza.fastmediasorter.ui.launcher.signal.source.FileTransferLauncherSignalSource
import com.sza.fastmediasorter.ui.launcher.signal.source.PlaybackLauncherSignalSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

/**
 * S1421 ADR-4: the set of signal sources is open for addition, so a new producer joins by declaring one
 * `@Binds @IntoSet` here rather than by growing `LauncherSignalRegistry`'s constructor.
 *
 * Declaration order below sets nothing. A Dagger `Set` multibinding guarantees no iteration order at all, so
 * the row's left-to-right order comes from `LauncherSignalKind`'s declaration order, which the registry
 * sorts by.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LauncherSignalModule {

    @Multibinds
    abstract fun launcherSignalSources(): Set<LauncherSignalSource>

    @Binds
    @IntoSet
    abstract fun bindPlaybackSource(source: PlaybackLauncherSignalSource): LauncherSignalSource

    @Binds
    @IntoSet
    abstract fun bindFileTransferSource(source: FileTransferLauncherSignalSource): LauncherSignalSource

    @Binds
    @IntoSet
    abstract fun bindBackgroundWorkSource(source: BackgroundWorkLauncherSignalSource): LauncherSignalSource
}
