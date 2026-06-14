package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.storage.NoLegalRestrictedTreeTargetPolicy
import com.sza.fastmediasorter.core.storage.VariantRestrictedTreeTargetPolicy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalRestrictedTreeTargetPolicyModule {

    @Binds
    abstract fun bindVariantRestrictedTreeTargetPolicy(
        impl: NoLegalRestrictedTreeTargetPolicy,
    ): VariantRestrictedTreeTargetPolicy
}
