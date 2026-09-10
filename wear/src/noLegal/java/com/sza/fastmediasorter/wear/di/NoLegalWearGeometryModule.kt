package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.capability.NoLegalWearGeometryDefaults
import com.sza.fastmediasorter.wear.domain.capability.WearGeometryDefaults
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the `noLegal` answers for [WearGeometryDefaults]. Compiled only into that flavor; its
 * `standard` counterpart is `StandardWearGeometryModule` (S2773).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalWearGeometryModule {

    @Binds
    abstract fun bindWearGeometryDefaults(
        impl: NoLegalWearGeometryDefaults
    ): WearGeometryDefaults
}
