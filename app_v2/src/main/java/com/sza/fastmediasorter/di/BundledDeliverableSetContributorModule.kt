package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSetContributor
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the multibound set of [BundledDeliverableSetContributor]s. Each flavor contributes the
 * deliverable sets that are still packaged in its base APK/AAB.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BundledDeliverableSetContributorModule {

    @Multibinds
    abstract fun bindBundledDeliverableSetContributors(): Set<@JvmSuppressWildcards BundledDeliverableSetContributor>
}