package com.sza.fastmediasorter_v2.core.di

import android.content.Context
import androidx.room.Room
import com.sza.fastmediasorter_v2.data.local.db.AppDatabase
import com.sza.fastmediasorter_v2.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter_v2.data.local.db.ResourceDao
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
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12
            )
            .fallbackToDestructiveMigration()
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
}
