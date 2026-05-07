package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.permissions.ContextualRationaleRepositoryImpl
import com.sza.fastmediasorter.data.permissions.PermissionRegistryRepositoryImpl
import com.sza.fastmediasorter.domain.repository.ContextualRationaleRepository
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PermissionModule {

    @Provides
    @Singleton
    fun providePermissionRegistryRepository(impl: PermissionRegistryRepositoryImpl): PermissionRegistryRepository = impl

    @Provides
    @Singleton
    fun provideContextualRationaleRepository(impl: ContextualRationaleRepositoryImpl): ContextualRationaleRepository = impl
}
