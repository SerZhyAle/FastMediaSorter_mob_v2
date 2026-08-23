package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.wear.WearFileTransferRepositoryImpl
import com.sza.fastmediasorter.data.wear.WearWatchMediaScannerImpl
import com.sza.fastmediasorter.data.wear.WearableDataLayerRepositoryImpl
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.scanner.WearWatchMediaScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for the `wearGms` source set (S0403) - mounted into Wear-capable flavors
 * (standard, noLegal, legacy). Binds the GMS-backed [WearableDataLayerRepositoryImpl].
 *
 * Paired with the same-named module in `src/wearStub/java/`. AGP mounts exactly one of the two per
 * flavor (see `app_v2/build.gradle.kts` `sourceSets`), mirroring `XrModule`/`NoOpXrModule`, so no
 * duplicate-binding conflict is possible.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearModule {

    @Binds
    @Singleton
    abstract fun bindWearableDataLayerRepository(
        impl: WearableDataLayerRepositoryImpl
    ): WearableDataLayerRepository

    @Binds
    @Singleton
    abstract fun bindWearWatchMediaScanner(
        impl: WearWatchMediaScannerImpl
    ): WearWatchMediaScanner

    @Binds
    @Singleton
    abstract fun bindWearFileTransferRepository(
        impl: WearFileTransferRepositoryImpl
    ): WearFileTransferRepository
}
