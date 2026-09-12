package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.capability.NoLegalWearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the `noLegal` answers for [WearRestrictedCapabilities]. Mirrors `StandardWearCapabilityModule` in
 * the other flavor source set; exactly one of the two is ever compiled, so a missing declaration here fails
 * the `noLegal` build rather than silently falling back to the store answers (S2486).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalWearCapabilityModule {

    @Binds
    abstract fun bindWearRestrictedCapabilities(
        impl: NoLegalWearRestrictedCapabilities
    ): WearRestrictedCapabilities
}
