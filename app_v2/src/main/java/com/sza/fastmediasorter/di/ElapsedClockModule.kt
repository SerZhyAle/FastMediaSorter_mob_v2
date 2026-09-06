package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.util.ElapsedClock
import com.sza.fastmediasorter.core.util.SystemElapsedClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the monotonic time source measured intervals read (S1411 ADR-8). */
@Module
@InstallIn(SingletonComponent::class)
object ElapsedClockModule {

    @Provides
    @Singleton
    fun provideElapsedClock(): ElapsedClock = SystemElapsedClock
}
