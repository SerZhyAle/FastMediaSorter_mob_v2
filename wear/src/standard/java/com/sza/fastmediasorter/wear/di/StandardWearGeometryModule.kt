package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.capability.StandardWearGeometryDefaults
import com.sza.fastmediasorter.wear.domain.capability.WearGeometryDefaults
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the `standard` answers for [WearGeometryDefaults]. Compiled only into that flavor; its
 * `noLegal` counterpart is `NoLegalWearGeometryModule`, and the two are never on the classpath
 * together, which is what lets both use a plain `@Binds` instead of a multibinding (S2773).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StandardWearGeometryModule {

    @Binds
    abstract fun bindWearGeometryDefaults(
        impl: StandardWearGeometryDefaults
    ): WearGeometryDefaults
}
