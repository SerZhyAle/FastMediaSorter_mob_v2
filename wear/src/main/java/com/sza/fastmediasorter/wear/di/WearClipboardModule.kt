package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.wear.AndroidWearClipboardTextSender
import com.sza.fastmediasorter.wear.domain.repository.WearClipboardTextSender
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S3109: binds the watch half of the text clipboard bridge.
 *
 * Its own module rather than a binding inside the system-information one: the clipboard shares no
 * contributor set and no lifetime with that report, and a binding filed under an unrelated subject
 * is one the next reader has to find by grepping.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearClipboardModule {

    @Binds
    abstract fun bindClipboardTextSender(
        impl: AndroidWearClipboardTextSender
    ): WearClipboardTextSender
}
