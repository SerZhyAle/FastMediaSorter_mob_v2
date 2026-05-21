package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.settings.search.LayoutSettingsSearchSource
import com.sza.fastmediasorter.ui.settings.search.LocalizedKeywordCollector
import com.sza.fastmediasorter.ui.settings.search.SettingsSearchKeywordCollector
import com.sza.fastmediasorter.ui.settings.search.SettingsSearchSource
import com.sza.fastmediasorter.ui.settings.search.SupportedMediaSection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/**
 * Hilt module wiring the settings-search auto-indexing pipeline.
 *
 * The actual contributions to the `@SupportedMediaSection Set<String>` come from the
 * per-flavor modules in `src/<flavor>/java/.../di/`. `@Multibinds` here makes the set
 * injectable even when no provider contributes (defensive — shouldn't happen at runtime).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsSearchModule {

    @Binds
    @Singleton
    abstract fun bindSettingsSearchSource(impl: LayoutSettingsSearchSource): SettingsSearchSource

    @Binds
    @Singleton
    abstract fun bindSettingsSearchKeywordCollector(
        impl: LocalizedKeywordCollector
    ): SettingsSearchKeywordCollector

    @Multibinds
    @SupportedMediaSection
    abstract fun supportedMediaSections(): Set<String>
}
