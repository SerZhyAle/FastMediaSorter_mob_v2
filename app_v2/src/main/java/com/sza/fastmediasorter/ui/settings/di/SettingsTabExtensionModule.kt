package com.sza.fastmediasorter.ui.settings.di

import com.sza.fastmediasorter.ui.settings.SettingsTabExtension
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the `Set<SettingsTabExtension>` multibinding (S0245).
 *
 * Flavor modules add entries via `@Binds @IntoSet`. Phone-only flavors contribute zero
 * entries, so the set resolves as empty - `SettingsPagerAdapter` shows only the static
 * 4 tabs.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsTabExtensionModule {
    @Multibinds
    abstract fun settingsTabExtensions(): Set<@JvmSuppressWildcards SettingsTabExtension>
}
