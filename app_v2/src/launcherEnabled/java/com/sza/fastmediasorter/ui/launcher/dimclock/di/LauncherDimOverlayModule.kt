package com.sza.fastmediasorter.ui.launcher.dimclock.di

import com.sza.fastmediasorter.ui.common.widget.dimclock.DimChipActionRouter
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimChipIconLoader
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockInteractionHandler
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyleProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusContentProvider
import com.sza.fastmediasorter.ui.launcher.dimclock.LauncherDimChipActionRouter
import com.sza.fastmediasorter.ui.launcher.dimclock.LauncherDimChipIconLoader
import com.sza.fastmediasorter.ui.launcher.dimclock.LauncherDimClockInteractionHandler
import com.sza.fastmediasorter.ui.launcher.dimclock.LauncherDimClockStyleProvider
import com.sza.fastmediasorter.ui.launcher.dimclock.LauncherDimStatusContentProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S3256: Hilt bindings for launcher-backed dim clock style and status providers.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LauncherDimOverlayModule {

    @Binds
    @Singleton
    abstract fun bindDimClockStyleProvider(
        impl: LauncherDimClockStyleProvider
    ): DimClockStyleProvider

    @Binds
    @Singleton
    abstract fun bindDimStatusContentProvider(
        impl: LauncherDimStatusContentProvider
    ): DimStatusContentProvider

    @Binds
    @Singleton
    abstract fun bindDimChipIconLoader(
        impl: LauncherDimChipIconLoader
    ): DimChipIconLoader

    @Binds
    @Singleton
    abstract fun bindDimChipActionRouter(
        impl: LauncherDimChipActionRouter
    ): DimChipActionRouter

    @Binds
    @Singleton
    abstract fun bindDimClockInteractionHandler(
        impl: LauncherDimClockInteractionHandler
    ): DimClockInteractionHandler
}
