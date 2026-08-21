package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadget
import com.sza.fastmediasorter.ui.launcher.gadget.NetworkIndicatorGadget
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * S1440: the network-monitor gadget family, supplied as ONE binding for the same reason
 * [TechnicalGadgets] and [SensorGadgets] are - `LauncherGadgetRegistry` would otherwise grow a
 * constructor parameter per tile and trip detekt's `constructorThreshold`. A second network tile joins
 * this list rather than the registry's constructor.
 *
 * Qualified because the payload is `List<LauncherGadget>`, the exact type the registry deals in; an
 * unqualified list binding would otherwise satisfy this injection point by accident.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NetworkGadgets

@Module
@InstallIn(SingletonComponent::class)
object NetworkGadgetModule {

    @Provides
    @Singleton
    @NetworkGadgets
    fun provideNetworkGadgets(networkIndicator: NetworkIndicatorGadget): List<LauncherGadget> =
        listOf(networkIndicator)
}
