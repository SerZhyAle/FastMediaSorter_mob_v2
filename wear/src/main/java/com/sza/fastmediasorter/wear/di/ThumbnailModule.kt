package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.thumbnail.WearThumbnailRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.WearThumbnailRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ThumbnailModule {

    /** Singleton because the cache is the point: a per-screen instance would re-read every scroll. */
    @Binds
    @Singleton
    abstract fun bindWearThumbnailRepository(
        impl: WearThumbnailRepositoryImpl
    ): WearThumbnailRepository
}
