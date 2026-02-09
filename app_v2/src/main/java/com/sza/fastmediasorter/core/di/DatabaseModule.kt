package com.sza.fastmediasorter.core.di

import android.content.Context
import androidx.room.Room
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
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
                AppDatabase.MIGRATION_6_7
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
}
