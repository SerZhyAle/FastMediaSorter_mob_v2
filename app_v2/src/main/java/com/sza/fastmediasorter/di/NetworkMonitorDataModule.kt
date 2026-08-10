package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.repository.NetworkMeasurementHistoryRepositoryImpl
import com.sza.fastmediasorter.data.repository.NetworkMonitorRepositoryImpl
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S1433: binds the Monitor's read path and its measurement history.
 *
 * Named `NetworkMonitorDataModule` rather than `NetworkMonitorModule` because the latter fully-qualified
 * name is already taken twice - `src/networkMonitor` and `src/networkMonitorDisabled` each declare one, and
 * every flavor mounts exactly one of them. A third declaration here would be a duplicate JVM class in all
 * six builds rather than a third choice.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkMonitorDataModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitorRepository(
        impl: NetworkMonitorRepositoryImpl,
    ): NetworkMonitorRepository

    @Binds
    @Singleton
    abstract fun bindNetworkMeasurementHistoryRepository(
        impl: NetworkMeasurementHistoryRepositoryImpl,
    ): NetworkMeasurementHistoryRepository
}
