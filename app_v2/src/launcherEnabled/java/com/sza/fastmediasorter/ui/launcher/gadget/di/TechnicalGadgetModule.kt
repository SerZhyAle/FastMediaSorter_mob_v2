package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.devicestatus.GetBatteryStatusUseCase
import com.sza.fastmediasorter.domain.usecase.devicestatus.GetMemoryStatusUseCase
import com.sza.fastmediasorter.domain.usecase.devicestatus.GetNetworkStatusUseCase
import com.sza.fastmediasorter.domain.usecase.devicestatus.GetStorageStatusUseCase
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadget
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.gadget.TechnicalGadget
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * S1178: the technical status gadgets, supplied as ONE binding for the same reason [SensorGadgets] is -
 * `LauncherGadgetRegistry` would otherwise grow a constructor parameter per tile and pass detekt's
 * `constructorThreshold`. Every later technical gadget joins this list rather than the constructor.
 *
 * Qualified because the payload is `List<LauncherGadget>`, the exact type the registry deals in; an
 * unqualified list binding would otherwise satisfy this injection point by accident.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TechnicalGadgets

@Module
@InstallIn(SingletonComponent::class)
object TechnicalGadgetModule {

    /**
     * Four entries, one class: the tile differs only by key, label, icon and which metric it reads
     * (ADR-2). A fifth technical gadget is a fifth entry here plus a branch in the formatter - never a
     * new view (strategic 5.3).
     */
    @Provides
    @Singleton
    @TechnicalGadgets
    fun provideTechnicalGadgets(
        network: GetNetworkStatusUseCase,
        battery: GetBatteryStatusUseCase,
        storage: GetStorageStatusUseCase,
        memory: GetMemoryStatusUseCase,
    ): List<LauncherGadget> = listOf(
        TechnicalGadget(
            key = LauncherGadgetRegistry.KEY_NETWORK,
            labelRes = R.string.launcher_gadget_network,
            iconRes = R.drawable.ic_wifi,
            provider = network,
        ),
        TechnicalGadget(
            key = LauncherGadgetRegistry.KEY_BATTERY,
            labelRes = R.string.launcher_gadget_battery,
            iconRes = R.drawable.ic_battery,
            provider = battery,
        ),
        TechnicalGadget(
            key = LauncherGadgetRegistry.KEY_STORAGE,
            labelRes = R.string.launcher_gadget_storage,
            iconRes = R.drawable.ic_storage,
            provider = storage,
        ),
        TechnicalGadget(
            key = LauncherGadgetRegistry.KEY_RESOURCES,
            labelRes = R.string.launcher_gadget_resources,
            iconRes = R.drawable.ic_speed,
            provider = memory,
        ),
    )
}
