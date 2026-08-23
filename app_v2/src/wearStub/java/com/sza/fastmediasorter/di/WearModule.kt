package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.wear.NoOpWearFileTransferRepository
import com.sza.fastmediasorter.data.wear.NoOpWearWatchMediaScanner
import com.sza.fastmediasorter.data.wear.NoOpWearableDataLayerRepository
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.scanner.WearWatchMediaScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for the `wearStub` source set (S0403) - mounted into non-Wear flavors
 * (lite, photos, legacy, vr) and the FOSS flavor. Binds the inert [NoOpWearableDataLayerRepository].
 *
 * S1951 added legacy: its applicationIdSuffix ".legacy" cannot match the watch app's
 * applicationId, and Play Services routes the Data Layer by that identity.
 *
 * Paired with the same-named module in `src/wearGms/java/` (the GMS impl binding). AGP mounts
 * exactly one of the two per flavor (see `app_v2/build.gradle.kts` `sourceSets`), mirroring
 * `NoOpXrModule`/`XrModule`, so no duplicate-binding conflict is possible.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearModule {

    @Binds
    @Singleton
    abstract fun bindWearableDataLayerRepository(
        impl: NoOpWearableDataLayerRepository
    ): WearableDataLayerRepository

    @Binds
    @Singleton
    abstract fun bindWearWatchMediaScanner(
        impl: NoOpWearWatchMediaScanner
    ): WearWatchMediaScanner

    @Binds
    @Singleton
    abstract fun bindWearFileTransferRepository(
        impl: NoOpWearFileTransferRepository
    ): WearFileTransferRepository
}
