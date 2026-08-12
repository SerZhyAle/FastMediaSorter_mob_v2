package com.sza.fastmediasorter.core.di

import android.content.Context
import androidx.room.Room
import com.sza.fastmediasorter.core.db.DatabaseResetNotice
import com.sza.fastmediasorter.core.util.CachedMediaMetadataExtractor
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.AppLaunchPanelTileDao
import com.sza.fastmediasorter.data.local.db.CachedFileListDao
import com.sza.fastmediasorter.data.local.db.DeviceProfileDao
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.FileMetadataCacheDao
import com.sza.fastmediasorter.data.local.db.InstalledAppDao
import com.sza.fastmediasorter.data.local.db.LauncherCellDao
import com.sza.fastmediasorter.data.local.db.LauncherJournalDao
import com.sza.fastmediasorter.data.local.db.LauncherLaunchStatsDao
import com.sza.fastmediasorter.data.local.db.LauncherPinDao
import com.sza.fastmediasorter.data.local.db.LauncherStateDao
import com.sza.fastmediasorter.data.local.db.MIGRATION_31_32
import com.sza.fastmediasorter.data.local.db.MIGRATION_32_33
import com.sza.fastmediasorter.data.local.db.MIGRATION_33_34
import com.sza.fastmediasorter.data.local.db.MIGRATION_34_35
import com.sza.fastmediasorter.data.local.db.MIGRATION_35_36
import com.sza.fastmediasorter.data.local.db.MIGRATION_36_37
import com.sza.fastmediasorter.data.local.db.MIGRATION_37_38
import com.sza.fastmediasorter.data.local.db.MIGRATION_38_39
import com.sza.fastmediasorter.data.local.db.MIGRATION_39_40
import com.sza.fastmediasorter.data.local.db.MIGRATION_40_41
import com.sza.fastmediasorter.data.local.db.MIGRATION_41_42
import com.sza.fastmediasorter.data.local.db.MIGRATION_42_43
import com.sza.fastmediasorter.data.local.db.MIGRATION_43_44
import com.sza.fastmediasorter.data.local.db.MIGRATION_44_45
import com.sza.fastmediasorter.data.local.db.MIGRATION_45_46
import com.sza.fastmediasorter.data.local.db.MIGRATION_46_47
import com.sza.fastmediasorter.data.local.db.MIGRATION_47_48
import com.sza.fastmediasorter.data.local.db.MIGRATION_48_49
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.data.local.db.NetworkMeasurementDao
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import com.sza.fastmediasorter.data.local.db.PlaybackPositionDao
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ScheduledOperationDao
import com.sza.fastmediasorter.data.local.db.SensorSeriesDao
import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideDao
import com.sza.fastmediasorter.data.local.db.StreamPlayOutcomeDao
import com.sza.fastmediasorter.data.local.db.StreamSourceDao
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
            Timber.e(e, "Database open/migration failed, resetting database: ${e.message}")
            // Back up the existing DB and record a notice so the first Activity can inform the user
            // (reason + backup location) instead of the prior silent Toast (S0731). recordReset never throws.
            DatabaseResetNotice.recordReset(context, DB_NAME, e)
            context.deleteDatabase(DB_NAME)
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
                AppDatabase.MIGRATION_29_30,
                AppDatabase.MIGRATION_30_31,
                MIGRATION_31_32,
                MIGRATION_32_33,
                MIGRATION_33_34,
                MIGRATION_34_35,
                MIGRATION_35_36,
                MIGRATION_36_37,
                MIGRATION_37_38,
                MIGRATION_38_39,
                MIGRATION_39_40,
                MIGRATION_40_41,
                MIGRATION_41_42,
                MIGRATION_42_43,
                MIGRATION_43_44,
                MIGRATION_44_45,
                MIGRATION_45_46,
                MIGRATION_46_47,
                MIGRATION_47_48,
                MIGRATION_48_49
            )
            // No fallbackToDestructiveMigration: a missing/failed migration now throws and is routed
            // through provideAppDatabase's recovery (backup + reset + user notice), not a silent
            // Room-internal table drop (S0731).
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
    fun provideStreamSourceDao(database: AppDatabase): StreamSourceDao {
        return database.streamSourceDao()
    }

    @Provides
    @Singleton
    fun provideStreamPlayOutcomeDao(database: AppDatabase): StreamPlayOutcomeDao {
        return database.streamPlayOutcomeDao()
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

    @Provides
    @Singleton
    fun provideDeviceProfileDao(database: AppDatabase): DeviceProfileDao {
        return database.deviceProfileDao()
    }

    @Provides
    @Singleton
    fun provideAppLaunchPanelTileDao(database: AppDatabase): AppLaunchPanelTileDao {
        return database.appLaunchPanelTileDao()
    }

    @Provides
    @Singleton
    fun provideLauncherCellDao(database: AppDatabase): LauncherCellDao {
        return database.launcherCellDao()
    }

    @Provides
    @Singleton
    fun provideLauncherJournalDao(database: AppDatabase): LauncherJournalDao {
        return database.launcherJournalDao()
    }

    @Provides
    @Singleton
    fun provideLauncherPinDao(database: AppDatabase): LauncherPinDao {
        return database.launcherPinDao()
    }

    @Provides
    @Singleton
    fun provideLauncherStateDao(database: AppDatabase): LauncherStateDao {
        return database.launcherStateDao()
    }

    @Provides
    @Singleton
    fun provideInstalledAppDao(database: AppDatabase): InstalledAppDao {
        return database.installedAppDao()
    }

    @Provides
    @Singleton
    fun provideLauncherLaunchStatsDao(database: AppDatabase): LauncherLaunchStatsDao {
        return database.launcherLaunchStatsDao()
    }

    @Provides
    @Singleton
    fun provideSensorSeriesDao(database: AppDatabase): SensorSeriesDao {
        return database.sensorSeriesDao()
    }

    @Provides
    @Singleton
    fun provideNetworkMeasurementDao(database: AppDatabase): NetworkMeasurementDao {
        return database.networkMeasurementDao()
    }
}
