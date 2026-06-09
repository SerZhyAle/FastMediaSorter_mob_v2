package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.delivery.DefaultBundledDeliverableSets
import com.sza.fastmediasorter.data.delivery.DeliverableCapabilityRepositoryImpl
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the on-demand delivery contract (S0386 Phase 02). The [BundledDeliverableSets] binding is
 * the default (all bundled) and is relocated to flavor source sets in Phase 05 once artifacts are
 * stripped per flavor (see `dev/FLAVOR_DEVELOPMENT_RULES.md`).
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
        impl: DefaultBundledDeliverableSets
    ): BundledDeliverableSets
}
