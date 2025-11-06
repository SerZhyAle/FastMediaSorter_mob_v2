package com.sza.fastmediasorter_v2.core.di

import com.sza.fastmediasorter_v2.data.repository.ResourceRepositoryImpl
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
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
}
