package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.network.WearNetworkDataSources
import com.sza.fastmediasorter.wear.data.repository.WearFolderLevelRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.WearNetworkFolderRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFolderLevelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearLocalFolderRepository
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkFolderRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S2694: the network folder walk's binding, kept out of `WearAppModule`.
 *
 * Not a stylistic split - that object sits on detekt's function ceiling, so the next provider added
 * to it fails the gate. A binding that belongs to one feature is cheaper to read here anyway, which
 * is the shape `WearNetworkMonitorModule` already set.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearNetworkFolderModule {

    @Provides
    @Singleton
    fun provideWearNetworkFolderRepository(
        sourceRepository: NetworkSourceRepository,
        dataSources: WearNetworkDataSources
    ): WearNetworkFolderRepository = WearNetworkFolderRepositoryImpl(sourceRepository, dataSources)

    @Provides
    @Singleton
    fun provideWearFolderLevelRepository(
        local: WearLocalFolderRepository,
        network: dagger.Lazy<WearNetworkFolderRepository>
    ): WearFolderLevelRepository = WearFolderLevelRepositoryImpl(local, network)
}
