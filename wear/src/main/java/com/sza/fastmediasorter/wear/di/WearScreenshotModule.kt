package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.wear.PixelCopyWearScreenCapture
import com.sza.fastmediasorter.wear.domain.repository.WearScreenCapture
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S3110: binds the watch half of the screenshot bridge.
 *
 * Its own module for the reason [WearClipboardModule] gives: the capture shares no contributor set
 * with the clipboard it sits beside, and a binding filed under an unrelated subject is one the next
 * reader has to find by grepping.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearScreenshotModule {

    @Binds
    abstract fun bindWearScreenCapture(
        impl: PixelCopyWearScreenCapture
    ): WearScreenCapture
}
