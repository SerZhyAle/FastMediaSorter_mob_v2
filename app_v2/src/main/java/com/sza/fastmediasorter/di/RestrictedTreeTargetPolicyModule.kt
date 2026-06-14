package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.storage.NoOpRestrictedTreeTargetPolicy
import com.sza.fastmediasorter.core.storage.RestrictedTreeTargetPolicy
import com.sza.fastmediasorter.core.storage.VariantRestrictedTreeTargetPolicy
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RestrictedTreeTargetPolicyModule {
    @BindsOptionalOf
    abstract fun bindVariantRestrictedTreeTargetPolicy(): VariantRestrictedTreeTargetPolicy

    companion object {
        @Provides
        @Singleton
        fun bindRestrictedTreeTargetPolicy(
            variantPolicy: Optional<VariantRestrictedTreeTargetPolicy>,
            noOpPolicy: NoOpRestrictedTreeTargetPolicy,
        ): RestrictedTreeTargetPolicy {
            return if (variantPolicy.isPresent) variantPolicy.get() else noOpPolicy
        }
    }
}
