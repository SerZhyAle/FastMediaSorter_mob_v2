package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.netmonitor.WearNetworkMonitorRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkMonitorRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Deliberately unscoped: reopening the Network Monitor must measure afresh rather than replay a
 * snapshot kept from the previous visit (owner ruling 2026-08-21). The component is only where the
 * binding lives, not a lifetime.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearNetworkMonitorModule {

    @Binds
    abstract fun bindWearNetworkMonitorRepository(
        impl: WearNetworkMonitorRepositoryImpl
    ): WearNetworkMonitorRepository
}
