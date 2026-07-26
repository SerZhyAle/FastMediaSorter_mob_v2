package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.data.weather.OpenMeteoWeatherProvider
import com.sza.fastmediasorter.data.weather.WeatherRepositoryImpl
import com.sza.fastmediasorter.domain.repository.WeatherRepository
import com.sza.fastmediasorter.domain.weather.WeatherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** S0426: the weather source and its cache. The shared OkHttpClient comes from [AppModule]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherModule {

    @Binds
    @Singleton
    abstract fun bindWeatherProvider(impl: OpenMeteoWeatherProvider): WeatherProvider

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository
}
