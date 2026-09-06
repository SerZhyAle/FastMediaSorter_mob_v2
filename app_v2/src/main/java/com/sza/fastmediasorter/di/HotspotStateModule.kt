package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.network.TetherBroadcastHotspotStateSource
import com.sza.fastmediasorter.domain.network.HotspotStateSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HotspotStateModule {

    @Binds
    @Singleton
    abstract fun bindHotspotStateSource(
        impl: TetherBroadcastHotspotStateSource
    ): HotspotStateSource
}
