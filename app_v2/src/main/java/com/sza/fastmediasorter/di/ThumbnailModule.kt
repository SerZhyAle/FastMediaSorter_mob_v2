package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.common.widget.MediaItemThumbnailBinder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides [MediaItemThumbnailBinder] as a singleton, per `docs/ui/PHONE_UI_COMPONENT_PATTERNS.md` section 2.2 (S3246). */
@Module
@InstallIn(SingletonComponent::class)
object ThumbnailModule {

    @Provides
    @Singleton
    fun provideMediaItemThumbnailBinder(): MediaItemThumbnailBinder = MediaItemThumbnailBinder()
}
