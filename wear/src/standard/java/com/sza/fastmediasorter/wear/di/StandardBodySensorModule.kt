package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.bodysensor.WithheldBodySensorDataSource
import com.sza.fastmediasorter.wear.domain.bodysensor.WearBodySensorDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the `standard` answer for [WearBodySensorDataSource]. Compiled only into that flavor; its
 * `noLegal` counterpart is `NoLegalBodySensorModule`, and the two are never on the classpath together,
 * which is what lets both use a plain `@Binds` instead of a multibinding (S2457).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StandardBodySensorModule {

    @Binds
    abstract fun bindWearBodySensorDataSource(
        impl: WithheldBodySensorDataSource
    ): WearBodySensorDataSource
}
