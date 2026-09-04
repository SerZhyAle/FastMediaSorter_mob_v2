package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.remote.sftp.ReachableEndpointProviderImpl
import com.sza.fastmediasorter.domain.networkmonitor.ReachableEndpointProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ReachableEndpointModule {

    // S2488: the watch-sync use case reads the endpoint choice through this seam, never through the
    // SFTP data layer directly.
    @Binds
    abstract fun bindReachableEndpointProvider(
        impl: ReachableEndpointProviderImpl
    ): ReachableEndpointProvider
}
