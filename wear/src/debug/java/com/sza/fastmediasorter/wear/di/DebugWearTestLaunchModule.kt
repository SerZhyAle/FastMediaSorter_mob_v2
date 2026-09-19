package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.ui.common.testlaunch.DebugWearTestLaunchOverrideReader
import com.sza.fastmediasorter.wear.ui.common.testlaunch.WearTestLaunchOverrideReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S3201: binds the parsing [WearTestLaunchOverrideReader] into debug builds. Its release counterpart
 * is `ReleaseWearTestLaunchModule`; the two build-type source sets are never on one classpath, so a
 * plain `@Binds` in each is enough.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugWearTestLaunchModule {

    @Binds
    abstract fun bindWearTestLaunchOverrideReader(
        impl: DebugWearTestLaunchOverrideReader
    ): WearTestLaunchOverrideReader
}
