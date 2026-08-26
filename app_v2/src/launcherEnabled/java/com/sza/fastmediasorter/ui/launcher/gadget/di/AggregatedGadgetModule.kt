package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadget
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * S1177: every qualified gadget collection, joined once, so the registry takes one of them instead of one
 * per family.
 *
 * This exists because the ceiling finally bit. Each of the last four tickets that added gadgets left a
 * comment in `LauncherGadgetRegistry` explaining that the next one must arrive as a qualified list rather
 * than a parameter - and each qualified list is itself a parameter, so the tenth one tripped detekt's
 * `LongParameterList` anyway. Joining them here means a fifth family costs a line in this file and nothing
 * in the registry.
 *
 * Order is the picker's order and is deliberate: home widgets, then sensors, then technical tiles, then
 * text tools - the same sequence the registry concatenated inline before.
 *
 * `@JvmSuppressWildcards` is load-bearing on every parameter, not decoration: Kotlin compiles a
 * `List<LauncherGadget>` parameter to Java `List<? extends LauncherGadget>`, which Dagger treats as a
 * different key from the `List<LauncherGadget>` each module provides, and the graph then fails at
 * `hiltJavaCompile` long after a Kotlin-only check reported clean.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AggregatedGadgets

@Module
@InstallIn(SingletonComponent::class)
object AggregatedGadgetModule {

    @Provides
    @Singleton
    @AggregatedGadgets
    fun provideAggregatedGadgets(
        @HomeWidgetGadgets homeWidgets: List<@JvmSuppressWildcards LauncherGadget>,
        @SensorGadgets sensors: List<@JvmSuppressWildcards LauncherGadget>,
        @TechnicalGadgets technical: List<@JvmSuppressWildcards LauncherGadget>,
        @TextToolGadgets textTools: List<@JvmSuppressWildcards LauncherGadget>,
        // S1754: the media windows come last - they are the only family that cannot be placed without
        // first picking a resource, so they sit after everything a tap can add outright.
        @MediaWindowGadgets mediaWindows: List<@JvmSuppressWildcards LauncherGadget>,
        // S1440: the network indicator comes last for the same reason the media windows sit before it -
        // it is the second family that cannot be placed without answering a question first, and the
        // picker reads best with the outright-placeable tiles at the top.
        @NetworkGadgets network: List<@JvmSuppressWildcards LauncherGadget>,
        // S1906: last for the reason the two families above it are late - a world clock cannot be placed
        // until its zone is picked, and the picker reads best with the outright-placeable tiles at the top.
        @TimeGadgets time: List<@JvmSuppressWildcards LauncherGadget>,
    ): List<LauncherGadget> =
        homeWidgets + sensors + technical + textTools + mediaWindows + network + time
}
