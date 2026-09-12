package com.sza.fastmediasorter.wear.di

import android.content.Context
import androidx.room.Room
import com.sza.fastmediasorter.wear.data.db.MotionHistoryDao
import com.sza.fastmediasorter.wear.data.db.WearMotionDatabase
import com.sza.fastmediasorter.wear.data.repository.MotionHistoryRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.MotionHistoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * S3014: Hilt module providing database, DAO, and repository for Wear Motion & Activity analytics.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearMotionModule {

    @Provides
    @Singleton
    @Suppress("TooGenericExceptionCaught")
    fun provideWearMotionDatabase(
        @ApplicationContext context: Context
    ): WearMotionDatabase = try {
        buildWearMotionDatabase(context).also { it.openHelper.writableDatabase }
    } catch (e: RuntimeException) {
        Timber.e(e, "Wear motion database failed to open - recreating it")
        context.deleteDatabase(WearMotionDatabase.DATABASE_NAME)
        buildWearMotionDatabase(context).also { it.openHelper.writableDatabase }
    }

    private fun buildWearMotionDatabase(context: Context): WearMotionDatabase =
        Room.databaseBuilder(
            context,
            WearMotionDatabase::class.java,
            WearMotionDatabase.DATABASE_NAME
        ).build()

    @Provides
    @Singleton
    fun provideMotionHistoryDao(database: WearMotionDatabase): MotionHistoryDao =
        database.motionHistoryDao()

    @Provides
    @Singleton
    fun provideMotionHistoryRepository(
        impl: MotionHistoryRepositoryImpl
    ): MotionHistoryRepository = impl
}
