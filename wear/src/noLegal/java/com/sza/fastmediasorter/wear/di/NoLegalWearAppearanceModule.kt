package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.capability.NoLegalWearAppearanceDefaults
import com.sza.fastmediasorter.wear.domain.capability.WearAppearanceDefaults
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the `noLegal` answers for [WearAppearanceDefaults]. Compiled only into that flavor; its
 * `standard` counterpart is `StandardWearAppearanceModule` (S3362).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalWearAppearanceModule {

    @Binds
    abstract fun bindWearAppearanceDefaults(
        impl: NoLegalWearAppearanceDefaults
    ): WearAppearanceDefaults
}
