package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.data.repository.SystemInfoGroupStateRepositoryImpl
import com.sza.fastmediasorter.domain.repository.SystemInfoGroupStateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** S4283: binds the remembered expanded state of the System information groups. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SystemInfoModule {

    @Binds
    @Singleton
    abstract fun bindSystemInfoGroupStateRepository(
        impl: SystemInfoGroupStateRepositoryImpl,
    ): SystemInfoGroupStateRepository
}
