package com.sza.fastmediasorter.core.xr.di

import com.sza.fastmediasorter.core.xr.NoOpXrDetectionFacade
import com.sza.fastmediasorter.core.xr.NoOpXrEntryGateway
import com.sza.fastmediasorter.core.xr.NoOpXrEnvironmentDetector
import com.sza.fastmediasorter.core.xr.XrDetectionFacade
import com.sza.fastmediasorter.core.xr.XrEntryGateway
import com.sza.fastmediasorter.core.xr.XrEnvironmentDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the `vrStub` source set - used by `standard`, `lite`, `photos`, `legacy`
 * flavors per `app_v2/build.gradle.kts` `sourceSets` block.
 *
 * Paired with `XrModule` in `src/vr/java/`. AGP mounts exactly one of the two per flavor -
 * no duplicate-binding conflict possible.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoOpXrModule {

    @Binds
    @Singleton
    abstract fun bindXrEnvironmentDetector(
        impl: NoOpXrEnvironmentDetector
    ): XrEnvironmentDetector

    @Binds
    @Singleton
    abstract fun bindXrDetectionFacade(
        impl: NoOpXrDetectionFacade
    ): XrDetectionFacade

    @Binds
    @Singleton
    abstract fun bindXrEntryGateway(
        impl: NoOpXrEntryGateway
    ): XrEntryGateway
}
