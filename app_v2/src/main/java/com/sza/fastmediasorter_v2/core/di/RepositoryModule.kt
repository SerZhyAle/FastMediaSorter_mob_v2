package com.sza.fastmediasorter_v2.core.di

import com.sza.fastmediasorter_v2.data.repository.NetworkCredentialsRepositoryImpl
import com.sza.fastmediasorter_v2.data.repository.ResourceRepositoryImpl
import com.sza.fastmediasorter_v2.data.repository.SettingsRepositoryImpl
import com.sza.fastmediasorter_v2.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindResourceRepository(
        impl: ResourceRepositoryImpl
    ): ResourceRepository
    
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
    
    @Binds
    @Singleton
    abstract fun bindNetworkCredentialsRepository(
        impl: NetworkCredentialsRepositoryImpl
    ): NetworkCredentialsRepository
}
