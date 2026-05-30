package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.browse.managers.NoLegalBrowseApkTileBadgeBinder
import com.sza.fastmediasorter.ui.browse.managers.VariantBrowseApkTileBadgeBinder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalBrowseApkTileBadgeModule {
    @Binds
    abstract fun bindBrowseApkTileBadgeBinder(
        impl: NoLegalBrowseApkTileBadgeBinder,
    ): VariantBrowseApkTileBadgeBinder
}
