package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * S0774: empty multibinding for [ScreenVideoRecordingController]. The screenCapture source set adds the
 * real `@IntoSet` impl; flavors without it resolve an empty set, keeping the scenario gated off.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenVideoRecordingControllerModule {

    @Multibinds
    abstract fun screenVideoRecordingControllers(): Set<ScreenVideoRecordingController>
}
