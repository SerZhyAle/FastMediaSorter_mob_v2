package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.radio.RadioControlContract
import com.sza.fastmediasorter.networkmonitor.NoOpNetworkMonitorContract
import com.sza.fastmediasorter.radio.NoOpRadioControlContract
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

    // S1441: the radio permissions live in src/networkMonitor, which these flavors do not mount, so the
    // controller reports unsupported and every tap keeps opening the system screen.
    @Provides
    @Singleton
    fun provideRadioControlContract(): RadioControlContract = NoOpRadioControlContract()
}
