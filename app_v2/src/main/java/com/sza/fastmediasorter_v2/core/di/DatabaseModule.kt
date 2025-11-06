package com.sza.fastmediasorter_v2.core.di

import android.content.Context
import androidx.room.Room
import com.sza.fastmediasorter_v2.data.local.db.AppDatabase
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
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideResourceDao(database: AppDatabase): ResourceDao {
        return database.resourceDao()
    }
}
