package com.sza.fastmediasorter.broadcast.di

import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastSourceControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BroadcastSourceModule {

    @Binds
    @Singleton
    abstract fun bindBroadcastSourceController(
        impl: BroadcastSourceControllerImpl
    ): BroadcastSourceController
}
