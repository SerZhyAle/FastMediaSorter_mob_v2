package com.sza.fastmediasorter.vr.di

import com.sza.fastmediasorter.core.memory.MemoryProfileFlavorOverride
import com.sza.fastmediasorter.vr.memory.VrMemoryProfileFlavorOverride
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VrMemoryProfileFlavorModule {

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindVrMemoryProfileFlavorOverride(
        impl: VrMemoryProfileFlavorOverride,
    ): MemoryProfileFlavorOverride
}