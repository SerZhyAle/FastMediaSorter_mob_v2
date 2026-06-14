package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.delivery.DeliverableSetContributor
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the multibound set of [DeliverableSetContributor]s (S0386 Phase 05). Empty by default in
 * `src/main`; each flavor source set binds its own contributor (`@IntoSet`) for the sets it ships.
 * Mirrors `OcrContributorModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeliverableSetContributorModule {

    @Multibinds
    abstract fun bindDeliverableSetContributors(): Set<@JvmSuppressWildcards DeliverableSetContributor>
}
