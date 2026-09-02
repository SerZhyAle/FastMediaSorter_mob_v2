package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadget
import com.sza.fastmediasorter.ui.launcher.gadget.WorldClockGadget
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * S1906: the cells that show a time other than the device's own.
 *
 * A collection for a single gadget is not ceremony: `LauncherGadgetRegistry` sits at detekt's
 * constructor threshold and its own KDoc states that the next gadget arrives as a qualified list rather
 * than as a parameter. No existing family fits - the world clock is neither a sensor tile, nor a
 * technical metric, nor a text tool.
 *
 * Qualified because the payload is `List<LauncherGadget>`, the exact type the registry deals in; an
 * unqualified list binding would otherwise satisfy that injection point by accident.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TimeGadgets

@Module
@InstallIn(SingletonComponent::class)
object TimeGadgetModule {

    @Provides
    @Singleton
    @TimeGadgets
    fun provideTimeGadgets(
        worldClock: WorldClockGadget,
    ): List<LauncherGadget> = listOf(worldClock)
}
