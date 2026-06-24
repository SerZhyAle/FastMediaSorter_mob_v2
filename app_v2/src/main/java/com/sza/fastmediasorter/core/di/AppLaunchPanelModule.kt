package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.data.repository.AppLaunchPanelRepositoryImpl
import com.sza.fastmediasorter.domain.repository.AppLaunchPanelRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** S0623: binds the app-launch panel repository; later phases reuse this module for panel UseCases. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppLaunchPanelModule {
    @Binds
    @Singleton
    abstract fun bindAppLaunchPanelRepository(
        impl: AppLaunchPanelRepositoryImpl
    ): AppLaunchPanelRepository
}
