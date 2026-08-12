package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.data.repository.InstalledAppsRepositoryImpl
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S1401: binds the cached installed-app list.
 *
 * Deliberately not part of `LauncherDesktopModule`: the app-launch panel's picker reads this cache in
 * flavors that have no launcher mode, so a launcher-named module would misstate who owns it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InstalledAppsModule {

    @Binds
    @Singleton
    abstract fun bindInstalledAppsRepository(
        impl: InstalledAppsRepositoryImpl
    ): InstalledAppsRepository
}
