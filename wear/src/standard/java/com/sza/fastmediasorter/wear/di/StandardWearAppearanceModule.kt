package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.capability.StandardWearAppearanceDefaults
import com.sza.fastmediasorter.wear.domain.capability.WearAppearanceDefaults
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the `standard` answers for [WearAppearanceDefaults]. Compiled only into that flavor; its
 * `noLegal` counterpart is `NoLegalWearAppearanceModule`, and the two are never on the classpath
 * together, which is what lets both use a plain `@Binds` instead of a multibinding (S3362).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StandardWearAppearanceModule {

    @Binds
    abstract fun bindWearAppearanceDefaults(
        impl: StandardWearAppearanceDefaults
    ): WearAppearanceDefaults
}
