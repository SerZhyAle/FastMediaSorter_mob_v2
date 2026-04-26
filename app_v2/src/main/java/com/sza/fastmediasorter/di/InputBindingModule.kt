package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.input.InputBindingDao
import com.sza.fastmediasorter.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InputBindingModule {

    @Provides
    @Singleton
    fun provideInputBindingDao(db: AppDatabase): InputBindingDao = db.inputBindingDao()
}
