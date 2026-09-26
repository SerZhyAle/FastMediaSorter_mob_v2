package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.repository.settings.WearFaceSlotsRepositoryImpl
import com.sza.fastmediasorter.domain.repository.WearFaceSlotsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WearFaceSlotsModule {

    @Binds
    @Singleton
    abstract fun bindWearFaceSlotsRepository(
        impl: WearFaceSlotsRepositoryImpl
    ): WearFaceSlotsRepository
}
