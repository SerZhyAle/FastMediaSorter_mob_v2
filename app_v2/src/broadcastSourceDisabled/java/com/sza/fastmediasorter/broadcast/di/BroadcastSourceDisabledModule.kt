package com.sza.fastmediasorter.broadcast.di

import com.sza.fastmediasorter.broadcast.BroadcastPreviewBinder
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.NoOpBroadcastPreviewBinder
import com.sza.fastmediasorter.broadcast.NoOpBroadcastSourceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BroadcastSourceDisabledModule {

    @Binds
    @Singleton
    abstract fun bindBroadcastSourceController(
        impl: NoOpBroadcastSourceController
    ): BroadcastSourceController

    @Binds
    @Singleton
    abstract fun bindBroadcastPreviewBinder(
        impl: NoOpBroadcastPreviewBinder
    ): BroadcastPreviewBinder
}
