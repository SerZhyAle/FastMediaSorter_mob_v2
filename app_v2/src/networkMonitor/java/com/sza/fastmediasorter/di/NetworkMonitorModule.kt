package com.sza.fastmediasorter.di

import android.content.Context
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.radio.RadioControlContract
import com.sza.fastmediasorter.networkmonitor.NetworkMonitorContractImpl
import com.sza.fastmediasorter.radio.RadioControlContractImpl
import com.sza.fastmediasorter.radio.RadioStateReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    // S1441: the radio permissions are declared by this source set, so this is the only place a build may
    // bind a controller that actually calls them.
    @Provides
    @Singleton
    fun provideRadioControlContract(@ApplicationContext context: Context): RadioControlContract =
        RadioControlContractImpl(context, RadioStateReader(context))
}
