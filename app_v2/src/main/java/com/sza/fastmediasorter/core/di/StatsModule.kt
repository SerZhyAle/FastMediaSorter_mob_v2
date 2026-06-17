package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.data.stats.StatsSinkImpl
import com.sza.fastmediasorter.domain.stats.StatsSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt bindings for the usage-statistics write path (S0473). */
@Module
@InstallIn(SingletonComponent::class)
abstract class StatsModule {

    @Binds
    @Singleton
    abstract fun bindStatsSink(impl: StatsSinkImpl): StatsSink
}
