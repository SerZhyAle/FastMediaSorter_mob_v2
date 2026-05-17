package com.sza.fastmediasorter.core.di

import android.content.Context
import android.widget.Toast
import androidx.room.Room
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.core.util.CachedMediaMetadataExtractor
import com.sza.fastmediasorter.data.local.db.CachedFileListDao
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.FileMetadataCacheDao
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import com.sza.fastmediasorter.data.local.db.PlaybackPositionDao
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ScheduledOperationDao
import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideDao
import com.sza.fastmediasorter.data.local.db.StreamingCacheDao
import com.sza.fastmediasorter.data.local.db.ThumbnailCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    private const val DB_NAME = "fastmediasorter_v2.db"

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return try {
            val db = buildDatabase(context)
            // Force-open to trigger migrations now (catch failures early)
            db.openHelper.writableDatabase
            db
        } catch (e: Exception) {
            Timber.e(e, "Database migration failed, resetting database: ${e.message}")
            // Delete the corrupted database and create a fresh one
            context.deleteDatabase(DB_NAME)
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        context.getString(R.string.database_reset_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (_: Exception) { /* Toast failure is non-critical */ }
            buildDatabase(context)
        }
    }

    private fun buildDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DB_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_18,
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
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25,
                AppDatabase.MIGRATION_25_26,
                AppDatabase.MIGRATION_26_27,
                AppDatabase.MIGRATION_27_28,
                AppDatabase.MIGRATION_28_29,
                AppDatabase.MIGRATION_29_30
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
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
    fun provideStereoFormatOverrideDao(database: AppDatabase): StereoFormatOverrideDao {
        return database.stereoFormatOverrideDao()
    }

    @Provides
    @Singleton
    fun providePendingRevocationDao(database: AppDatabase): PendingRevocationDao {
        return database.pendingRevocationDao()
    }

    @Provides
    @Singleton
    fun provideScheduledOperationDao(database: AppDatabase): ScheduledOperationDao {
        return database.scheduledOperationDao()
    }

    @Provides
    @Singleton
    fun provideDuplicateHashCacheDao(database: AppDatabase): com.sza.fastmediasorter.data.local.db.DuplicateHashCacheDao {
        return database.duplicateHashCacheDao()
    }

    @Provides
    @Singleton
    fun provideStreamingCacheDao(database: AppDatabase): StreamingCacheDao {
        return database.streamingCacheDao()
    }

    @Provides
    @Singleton
    fun provideCachedMediaMetadataExtractor(
        dao: FileMetadataCacheDao
    ): CachedMediaMetadataExtractor = CachedMediaMetadataExtractor(dao)
}
