package com.sza.fastmediasorter.ui.settings.vr.di

import com.sza.fastmediasorter.ui.settings.SettingsTabExtension
import com.sza.fastmediasorter.ui.settings.vr.VrSettingsTabExtension
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Adds the VR Settings tab to the `Set<SettingsTabExtension>` multibinding. Mounted only
 * in the `vr` source set - `noLegal` picks it up via the source-set inheritance configured
 * in `app_v2/build.gradle.kts` (S0245 Phase 01).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VrSettingsExtensionModule {
    @Binds
    @IntoSet
    abstract fun bindVrSettingsTabExtension(
        impl: VrSettingsTabExtension
    ): SettingsTabExtension
}
