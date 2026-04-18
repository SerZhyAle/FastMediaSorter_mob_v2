package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.player.contracts.StereoDetectionFacade
import com.sza.fastmediasorter.ui.player.contracts.StereoDetectionFacadeImpl
import com.sza.fastmediasorter.ui.player.entry.PlayerEntryCoordinator
import com.sza.fastmediasorter.ui.player.entry.PlayerEntryCoordinatorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt bindings for shared player contracts used across all flavors.
 * VR-specific bindings live in vr/di/VrModule.kt.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerContractsModule {

    @Binds
    abstract fun bindStereoDetectionFacade(
        impl: StereoDetectionFacadeImpl
    ): StereoDetectionFacade

    @Binds
    abstract fun bindPlayerEntryCoordinator(
        impl: PlayerEntryCoordinatorImpl
    ): PlayerEntryCoordinator
}
