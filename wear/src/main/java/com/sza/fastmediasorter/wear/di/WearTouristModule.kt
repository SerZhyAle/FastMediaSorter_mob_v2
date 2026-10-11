package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.tourist.AndroidWearTouristRepository
import com.sza.fastmediasorter.wear.data.tourist.DataLayerWearTouristWeatherRepository
import com.sza.fastmediasorter.wear.domain.repository.WearTouristRepository
import com.sza.fastmediasorter.wear.domain.repository.WearTouristWeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S3007: Hilt binding module for [WearTouristRepository]; S4152 adds [WearTouristWeatherRepository].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearTouristModule {

    @Binds
    @Singleton
    abstract fun bindWearTouristRepository(
        impl: AndroidWearTouristRepository,
    ): WearTouristRepository

    @Binds
    @Singleton
    abstract fun bindWearTouristWeatherRepository(
        impl: DataLayerWearTouristWeatherRepository,
    ): WearTouristWeatherRepository
}
