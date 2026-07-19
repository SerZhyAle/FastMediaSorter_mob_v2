package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.launcher.LauncherModeContractImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S0404: flavors mounting `src/launcherDisabled/java` (lite / photos / legacy / vr) have no home
 * surface, so the capability reports unavailable and every launcher entry point stays hidden.
 */
@Module
@InstallIn(SingletonComponent::class)
object LauncherModeModule {

    @Provides
    @Singleton
    fun provideLauncherModeContract(): LauncherModeContract = LauncherModeContractImpl()
}
