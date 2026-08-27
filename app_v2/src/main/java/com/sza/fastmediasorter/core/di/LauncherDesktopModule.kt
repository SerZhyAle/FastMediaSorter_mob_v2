package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.data.repository.LauncherDesktopRepositoryImpl
import com.sza.fastmediasorter.data.repository.LauncherJournalRepositoryImpl
import com.sza.fastmediasorter.data.repository.LauncherPinsRepositoryImpl
import com.sza.fastmediasorter.data.repository.LauncherSectionVisibilityRepositoryImpl
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
import com.sza.fastmediasorter.domain.repository.LauncherSectionVisibilityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** S0404: binds the launcher desktop / journal / taskbar-pin / section-visibility repositories. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LauncherDesktopModule {

    @Binds
    @Singleton
    abstract fun bindLauncherDesktopRepository(
        impl: LauncherDesktopRepositoryImpl
    ): LauncherDesktopRepository

    @Binds
    @Singleton
    abstract fun bindLauncherJournalRepository(
        impl: LauncherJournalRepositoryImpl
    ): LauncherJournalRepository

    @Binds
    @Singleton
    abstract fun bindLauncherPinsRepository(
        impl: LauncherPinsRepositoryImpl
    ): LauncherPinsRepository

    @Binds
    @Singleton
    abstract fun bindLauncherSectionVisibilityRepository(
        impl: LauncherSectionVisibilityRepositoryImpl
    ): LauncherSectionVisibilityRepository
}
