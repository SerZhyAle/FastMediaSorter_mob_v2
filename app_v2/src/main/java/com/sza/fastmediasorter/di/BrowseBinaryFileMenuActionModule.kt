package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.browse.managers.BrowseBinaryFileMenuAction
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
interface BrowseBinaryFileMenuActionModule {
    @Multibinds
    fun binaryFileMenuActions(): Set<BrowseBinaryFileMenuAction>
}