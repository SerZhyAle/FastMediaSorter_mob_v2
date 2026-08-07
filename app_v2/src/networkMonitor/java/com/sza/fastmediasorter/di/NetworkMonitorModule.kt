package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.networkmonitor.NetworkMonitorContractImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S1433: flavors mounting `src/networkMonitor/java` (standard / noLegal) ship the diagnostic
 * program, so the capability reports available.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkMonitorModule {

    @Provides
    @Singleton
    fun provideNetworkMonitorContract(): NetworkMonitorContract = NetworkMonitorContractImpl()
}
