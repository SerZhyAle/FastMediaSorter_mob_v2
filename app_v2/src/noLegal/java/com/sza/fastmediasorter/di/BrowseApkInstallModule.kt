package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.browse.managers.BrowseBinaryFileMenuAction
import com.sza.fastmediasorter.ui.browse.managers.BrowseApkInstallHandlerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module: contributes the noLegal APK install action into the binary-file menu hook set.
 *
 * S0183: APK install from Browse (noLegal only).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BrowseApkInstallModule {
    @Binds
    @IntoSet
    abstract fun bindApkInstallAction(impl: BrowseApkInstallHandlerImpl): BrowseBinaryFileMenuAction
}
