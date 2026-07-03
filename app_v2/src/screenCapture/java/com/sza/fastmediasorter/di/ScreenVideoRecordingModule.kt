package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import com.sza.fastmediasorter.screencapture.ScreenVideoRecordingControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * S0774: binds the real screen-recording controller into the multibinding set for screenCapture-enabled
 * flavors (standard `fms.screenCapture=on` + noLegal). Absent flavors keep the empty set from
 * `ScreenVideoRecordingControllerModule`, gating the scenario off.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenVideoRecordingModule {

    @Binds
    @IntoSet
    abstract fun bindScreenVideoRecordingController(
        impl: ScreenVideoRecordingControllerImpl
    ): ScreenVideoRecordingController
}
