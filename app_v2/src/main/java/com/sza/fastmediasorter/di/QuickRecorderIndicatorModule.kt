package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.widget.QuickRecorderIndicatorController
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class QuickRecorderIndicatorModule {

    @Multibinds
    abstract fun controllers(): Set<QuickRecorderIndicatorController>
}
