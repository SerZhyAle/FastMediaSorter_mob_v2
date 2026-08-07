package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.networkmonitor.NoOpNetworkMonitorContract
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S1433: flavors mounting `src/networkMonitorDisabled/java` do not ship the diagnostic program,
 * so the capability reports unavailable and every consumer hides its entry point.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkMonitorModule {

    @Provides
    @Singleton
    fun provideNetworkMonitorContract(): NetworkMonitorContract = NoOpNetworkMonitorContract()
}
