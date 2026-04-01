package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.repository.DuplicateHashRepositoryImpl
import com.sza.fastmediasorter.domain.repository.DuplicateHashRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DuplicateHashModule {

    @Binds
    @Singleton
    abstract fun bindDuplicateHashRepository(
        impl: DuplicateHashRepositoryImpl
    ): DuplicateHashRepository
}
