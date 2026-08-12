package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.permissions.PermissionRegistryRepositoryImpl
import com.sza.fastmediasorter.data.permissions.PermissionRequestMarkerRepositoryImpl
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import com.sza.fastmediasorter.domain.repository.PermissionRequestMarkerRepository
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
    fun providePermissionRequestMarkerRepository(
        impl: PermissionRequestMarkerRepositoryImpl,
    ): PermissionRequestMarkerRepository = impl
}
