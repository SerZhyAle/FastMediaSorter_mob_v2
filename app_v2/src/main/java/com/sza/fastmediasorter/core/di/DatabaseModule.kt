package com.sza.fastmediasorter.core.di

import android.content.Context
import androidx.room.Room
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.core.util.CachedMediaMetadataExtractor
import com.sza.fastmediasorter.data.local.db.CachedFileListDao
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.FileMetadataCacheDao
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import com.sza.fastmediasorter.data.local.db.PlaybackPositionDao
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ThumbnailCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fastmediasorter_v2.db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17
            )
            .build()
    }
    
    @Provides
    @Singleton
    fun provideResourceDao(database: AppDatabase): ResourceDao {
        return database.resourceDao()
    }
    
    @Provides
    @Singleton
    fun provideNetworkCredentialsDao(database: AppDatabase): NetworkCredentialsDao {
        return database.networkCredentialsDao()
    }

    @Provides
    @Singleton
    fun provideFavoritesDao(database: AppDatabase): FavoritesDao {
        return database.favoritesDao()
    }
    
    @Provides
    @Singleton
    fun providePlaybackPositionDao(database: AppDatabase): PlaybackPositionDao {
        return database.playbackPositionDao()
    }
    
    @Provides
    @Singleton
    fun provideThumbnailCacheDao(database: AppDatabase): ThumbnailCacheDao {
        return database.thumbnailCacheDao()
    }
    
    @Provides
    @Singleton
    fun provideCachedFileListDao(database: AppDatabase): CachedFileListDao {
        return database.cachedFileListDao()
    }

    @Provides
    @Singleton
    fun provideFileMetadataCacheDao(database: AppDatabase): FileMetadataCacheDao {
        return database.fileMetadataCacheDao()
    }

    @Provides
    @Singleton
    fun providePendingRevocationDao(database: AppDatabase): PendingRevocationDao {
        return database.pendingRevocationDao()
    }

    @Provides
    @Singleton
    fun provideCachedMediaMetadataExtractor(
        dao: FileMetadataCacheDao
    ): CachedMediaMetadataExtractor = CachedMediaMetadataExtractor(dao)
}
