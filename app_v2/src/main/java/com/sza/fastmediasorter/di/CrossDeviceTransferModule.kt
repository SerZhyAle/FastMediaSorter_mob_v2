package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.cloud.GoogleDriveCrossDeviceTransferRepositoryImpl
import com.sza.fastmediasorter.data.transfer.IncomingTransferFileStore
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.ui.browse.managers.BrowseBinaryFileMenuAction
import com.sza.fastmediasorter.ui.browse.managers.BrowseSendToDeviceMenuAction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/** S3040: binds the cross-device packet queue to its Google Drive AppData implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CrossDeviceTransferModule {

    @Binds
    @Singleton
    abstract fun bindCrossDeviceTransferRepository(
        impl: GoogleDriveCrossDeviceTransferRepositoryImpl
    ): CrossDeviceTransferRepository

    @Binds
    @Singleton
    abstract fun bindIncomingTransferFileSink(
        impl: IncomingTransferFileStore
    ): IncomingTransferFileSink

    /** Into the file-sheet action set, beside the flavor-specific ones (S3040). */
    @Binds
    @IntoSet
    abstract fun bindSendToDeviceMenuAction(
        impl: BrowseSendToDeviceMenuAction
    ): BrowseBinaryFileMenuAction
}
