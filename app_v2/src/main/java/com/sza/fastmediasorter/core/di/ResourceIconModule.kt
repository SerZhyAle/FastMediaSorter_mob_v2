package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.domain.icon.ResourceIconProvider
import com.sza.fastmediasorter.ui.icon.ResourceIconProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** S1289: binds the composed resource-icon provider behind its domain-owned contract. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ResourceIconModule {

    @Binds
    @Singleton
    abstract fun bindResourceIconProvider(
        impl: ResourceIconProviderImpl
    ): ResourceIconProvider
}
