package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.ui.common.testlaunch.ReleaseWearTestLaunchOverrideReader
import com.sza.fastmediasorter.wear.ui.common.testlaunch.WearTestLaunchOverrideReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** S3201: binds the no-op [WearTestLaunchOverrideReader] into release builds; see `DebugWearTestLaunchModule`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReleaseWearTestLaunchModule {

    @Binds
    abstract fun bindWearTestLaunchOverrideReader(
        impl: ReleaseWearTestLaunchOverrideReader
    ): WearTestLaunchOverrideReader
}
