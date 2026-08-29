package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.launcher.ConfiguredWidgetInstanceCleaner
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
 *
 * S2217: no launcher surface also means no configured widget instances can exist here, so the
 * reset's cleanup seam binds to a no-op - the reset use case compiles in every flavor and needs
 * the binding either way.
 */
@Module
@InstallIn(SingletonComponent::class)
object LauncherModeModule {

    @Provides
    @Singleton
    fun provideLauncherModeContract(): LauncherModeContract = LauncherModeContractImpl()

    @Provides
    @Singleton
    fun provideConfiguredWidgetInstanceCleaner(): ConfiguredWidgetInstanceCleaner =
        ConfiguredWidgetInstanceCleaner { }
}
