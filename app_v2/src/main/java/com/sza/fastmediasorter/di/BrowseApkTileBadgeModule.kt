package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.browse.managers.BrowseApkTileBadgeBinder
import com.sza.fastmediasorter.ui.browse.managers.NoOpBrowseApkTileBadgeBinder
import com.sza.fastmediasorter.ui.browse.managers.VariantBrowseApkTileBadgeBinder
import dagger.BindsOptionalOf
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BrowseApkTileBadgeModule {
    @BindsOptionalOf
    abstract fun bindVariantBrowseApkTileBadgeBinder(): VariantBrowseApkTileBadgeBinder

    companion object {
        @Provides
        @Singleton
        fun bindBrowseApkTileBadgeBinder(
            variantBinder: Optional<VariantBrowseApkTileBadgeBinder>,
            noOpBinder: NoOpBrowseApkTileBadgeBinder,
        ): BrowseApkTileBadgeBinder {
            return if (variantBinder.isPresent) variantBinder.get() else noOpBinder
        }
    }
}
