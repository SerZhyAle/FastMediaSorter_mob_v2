package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.repository.ClockDialStyleSource
import com.sza.fastmediasorter.ui.common.widget.dimclock.DefaultDimChipActionRouter
import com.sza.fastmediasorter.ui.common.widget.dimclock.DefaultDimChipIconLoader
import com.sza.fastmediasorter.ui.common.widget.dimclock.DefaultDimClockInteractionHandler
import com.sza.fastmediasorter.ui.common.widget.dimclock.DefaultDimClockStyleProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DefaultDimStatusContentProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimChipActionRouter
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimChipIconLoader
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockInteractionHandler
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyleProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusContentProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S3256: Hilt bindings for default dim clock style and status providers in launcherDisabled flavors.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LauncherDisabledDimOverlayModule {

    @Binds
    @Singleton
    abstract fun bindDimClockStyleProvider(
        impl: DefaultDimClockStyleProvider
    ): DimClockStyleProvider

    @Binds
    @Singleton
    abstract fun bindDimStatusContentProvider(
        impl: DefaultDimStatusContentProvider
    ): DimStatusContentProvider

    @Binds
    @Singleton
    abstract fun bindDimChipIconLoader(
        impl: DefaultDimChipIconLoader
    ): DimChipIconLoader

    @Binds
    @Singleton
    abstract fun bindDimChipActionRouter(
        impl: DefaultDimChipActionRouter
    ): DimChipActionRouter

    @Binds
    @Singleton
    abstract fun bindDimClockInteractionHandler(
        impl: DefaultDimClockInteractionHandler
    ): DimClockInteractionHandler

    @Binds
    @Singleton
    abstract fun bindClockDialStyleSource(
        impl: NoClockDialStyleSource
    ): ClockDialStyleSource
}
