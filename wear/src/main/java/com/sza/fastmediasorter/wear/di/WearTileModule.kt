package com.sza.fastmediasorter.wear.di

import android.content.Context
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.repository.WearTileAssignmentRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.WearTileAssignmentRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for Wear OS tiles.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearTileModule {

    @Provides
    @Singleton
    fun provideWearTileAssignmentRepository(
        @ApplicationContext context: Context,
        gson: Gson
    ): WearTileAssignmentRepository = WearTileAssignmentRepositoryImpl(context, gson)
}
