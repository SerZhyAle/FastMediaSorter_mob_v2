package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.delivery.ContributedBundledDeliverableSets
import com.sza.fastmediasorter.data.delivery.DeliverableCapabilityRepositoryImpl
import com.sza.fastmediasorter.data.delivery.RealDeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSetContributor
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetContributor
import com.sza.fastmediasorter.domain.delivery.DeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.DeliverableSourceDescriptor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the on-demand delivery contract and channel (S0386 Phases 02/04). Flavor source sets now
 * contribute both bundled-state and payload descriptors, so the base app can answer `already in
 * this build vs prompt/download` without `BuildConfig` gates in `src/main`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeliveryModule {

    @Binds
    abstract fun bindDeliverableCapabilityRepository(
        impl: DeliverableCapabilityRepositoryImpl
    ): DeliverableCapabilityRepository

    @Binds
    abstract fun bindBundledDeliverableSets(
        impl: ContributedBundledDeliverableSets
    ): BundledDeliverableSets

    @Binds
    abstract fun bindDeliverableSetDownloader(
        impl: RealDeliverableSetDownloader
    ): DeliverableSetDownloader

    @Binds
    @Singleton
    abstract fun bindDeliverableDownloadRunner(
        impl: com.sza.fastmediasorter.data.delivery.DeliverableDownloadRunnerImpl
    ): com.sza.fastmediasorter.domain.delivery.DeliverableDownloadRunner

    @Binds
    @Singleton
    abstract fun bindDeliverableInventory(
        impl: com.sza.fastmediasorter.data.delivery.DeliverableInventoryImpl
    ): com.sza.fastmediasorter.domain.delivery.DeliverableInventory

    /** S1483: freshness of the unpinned artwork payloads is read from the mirror's manifest. */
    @Binds
    @Singleton
    abstract fun bindArtworkManifestSource(
        impl: com.sza.fastmediasorter.data.delivery.ArtworkManifestClient
    ): com.sza.fastmediasorter.domain.delivery.ArtworkManifestSource

    companion object {

        /**
         * Compiled bundled descriptors keyed by set, merged from every flavor-bound
         * [DeliverableSetContributor] (S0386 Phase 05). Empty when no flavor contributes (the channel
         * stays inert), so a flavor declares only the sets it ships.
         */
        @Provides
        @Singleton
        fun provideBundledDescriptors(
            contributors: Set<@JvmSuppressWildcards DeliverableSetContributor>
        ): Map<DeliverableSet, @JvmSuppressWildcards DeliverableSourceDescriptor> =
            buildMap { contributors.forEach { putAll(it.descriptors()) } }
    }
}
