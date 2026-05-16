package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.memory.DefaultMemoryProfileFlavorOverride
import com.sza.fastmediasorter.core.memory.MemoryProfileCoordinator
import com.sza.fastmediasorter.core.memory.MemoryProfileCoordinatorImpl
import com.sza.fastmediasorter.core.memory.MemoryProfileFlavorOverride
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryProfileModule {

    @Binds
    @Singleton
    abstract fun bindMemoryProfileCoordinator(
        impl: MemoryProfileCoordinatorImpl,
    ): MemoryProfileCoordinator

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindDefaultMemoryProfileFlavorOverride(
        impl: DefaultMemoryProfileFlavorOverride,
    ): MemoryProfileFlavorOverride
}