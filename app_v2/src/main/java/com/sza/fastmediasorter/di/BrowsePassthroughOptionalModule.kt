package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.browse.managers.BrowsePassthroughCaptureProvider
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface BrowsePassthroughOptionalModule {
    @BindsOptionalOf
    fun optionalPassthroughCaptureProvider(): BrowsePassthroughCaptureProvider
}
