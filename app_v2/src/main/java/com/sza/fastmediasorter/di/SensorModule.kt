package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.sensors.DeviceSensorAvailabilityRepositoryImpl
import com.sza.fastmediasorter.data.sensors.SensorSeriesRepositoryImpl
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.repository.SensorSeriesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S1179: bindings for the sensor readings layer.
 *
 * Only the two repositories need a binding - the three reading sources are `@Singleton` classes with
 * an `@Inject constructor`, which Hilt resolves without a `@Provides`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds
    @Singleton
    abstract fun bindSensorAvailabilityRepository(
        impl: DeviceSensorAvailabilityRepositoryImpl,
    ): SensorAvailabilityRepository

    @Binds
    @Singleton
    abstract fun bindSensorSeriesRepository(
        impl: SensorSeriesRepositoryImpl,
    ): SensorSeriesRepository
}
