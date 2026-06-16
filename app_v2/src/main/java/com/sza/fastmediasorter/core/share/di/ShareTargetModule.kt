package com.sza.fastmediasorter.core.share.di

import com.sza.fastmediasorter.core.share.ShareTarget
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the `Set<ShareTarget>` multibinding seam (S0452 foundation).
 *
 * `@Multibinds` lets the set inject empty until a target ticket (S0443-S0446) or Phase 04 seeds an
 * entry via `@Provides @IntoSet` / `@Binds @IntoSet`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ShareTargetModule {

    @Multibinds
    abstract fun shareTargets(): Set<ShareTarget>
}
