package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.capability.StandardWearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the `standard` answers for [WearRestrictedCapabilities]. Compiled only into that flavor; its
 * `noLegal` counterpart is `NoLegalWearCapabilityModule`, and the two are never on the classpath together,
 * which is what lets both use a plain `@Binds` instead of a multibinding (S2486).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StandardWearCapabilityModule {

    @Binds
    abstract fun bindWearRestrictedCapabilities(
        impl: StandardWearRestrictedCapabilities
    ): WearRestrictedCapabilities
}
