package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.repository.MediaStoreMusicTrackRepository
import com.sza.fastmediasorter.domain.repository.MusicTrackRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the device media store as the audio chooser source (S2792). */
@Module
@InstallIn(SingletonComponent::class)
abstract class MusicTrackModule {

    @Binds
    @Singleton
    abstract fun bindMusicTrackRepository(
        impl: MediaStoreMusicTrackRepository,
    ): MusicTrackRepository
}
