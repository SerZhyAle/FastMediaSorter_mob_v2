package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.cloud.GoogleDriveCrossDeviceTransferRepositoryImpl
import com.sza.fastmediasorter.data.repository.settings.DriveChannelSettingsStore
import com.sza.fastmediasorter.data.transfer.IncomingTransferFileStore
import com.sza.fastmediasorter.data.transfer.RendezvousLocalDeviceIdentitySource
import com.sza.fastmediasorter.data.transfer.drive.DriveChannelPollCoordinator
import com.sza.fastmediasorter.data.transfer.drive.DriveFileChannelImpl
import com.sza.fastmediasorter.data.transfer.drive.DriveProducerLookup
import com.sza.fastmediasorter.data.transfer.drive.ForegroundSignal
import com.sza.fastmediasorter.data.transfer.drive.ProcessForegroundSignal
import com.sza.fastmediasorter.data.transfer.drive.RendezvousDriveProducerLookup
import com.sza.fastmediasorter.data.transfer.drive.RendezvousOwnDriveDevices
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DriveFileChannel
import com.sza.fastmediasorter.domain.transfer.DriveQueueSnapshots
import com.sza.fastmediasorter.domain.transfer.OwnDriveDevices
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

    @Binds
    @Singleton
    abstract fun bindDriveChannelSettings(
        impl: DriveChannelSettingsStore
    ): DriveChannelSettings

    @Binds
    @Singleton
    abstract fun bindLocalDeviceIdentitySource(
        impl: RendezvousLocalDeviceIdentitySource
    ): LocalDeviceIdentitySource

    /** S4231: the one poll of the Drive queue every consumer shares. */
    @Binds
    @Singleton
    abstract fun bindDriveQueueSnapshots(
        impl: DriveChannelPollCoordinator
    ): DriveQueueSnapshots

    @Binds
    @Singleton
    abstract fun bindDriveFileChannel(
        impl: DriveFileChannelImpl
    ): DriveFileChannel

    @Binds
    abstract fun bindDriveProducerLookup(
        impl: RendezvousDriveProducerLookup
    ): DriveProducerLookup

    @Binds
    abstract fun bindOwnDriveDevices(
        impl: RendezvousOwnDriveDevices
    ): OwnDriveDevices

    @Binds
    abstract fun bindForegroundSignal(
        impl: ProcessForegroundSignal
    ): ForegroundSignal

    /** Into the file-sheet action set, beside the flavor-specific ones (S3040). */
    @Binds
    @IntoSet
    abstract fun bindSendToDeviceMenuAction(
        impl: BrowseSendToDeviceMenuAction
    ): BrowseBinaryFileMenuAction
}
