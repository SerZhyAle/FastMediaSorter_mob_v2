package com.sza.fastmediasorter.data.transfer.drive

import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.transfer.OwnDriveDevice
import com.sza.fastmediasorter.domain.transfer.OwnDriveDevices
import com.sza.fastmediasorter.domain.usecase.transfer.GetOwnDriveDevicesUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** S4231: the targets of a send via Google Drive - every own device record but this device's. */
class RendezvousOwnDriveDevicesTest {

    @Test
    fun `this device is never offered as a target`() = runTest {
        val records = mockk<GoogleDriveSftpRendezvousDataSource>()
        val identity = mockk<LocalDeviceIdentitySource>()
        coEvery { identity.current() } returns LocalDeviceIdentity("dev-self", "Phone")
        coEvery { records.snapshot() } returns Result.success(
            GoogleDriveSftpRendezvousDataSource.Snapshot(
                devices = listOf(entry("dev-self", "Phone", SEEN_OLD), entry("dev-tv", "TV", SEEN_NEW)),
                resources = emptyList(),
                requests = emptyList()
            )
        )

        val devices = RendezvousOwnDriveDevices(records, identity).list().getOrThrow()

        assertEquals(listOf(OwnDriveDevice("dev-tv", "TV", SEEN_NEW)), devices)
    }

    @Test
    fun `the most recently seen device comes first`() = runTest {
        val port = mockk<OwnDriveDevices>()
        coEvery { port.list() } returns Result.success(
            listOf(
                OwnDriveDevice("a", "A", SEEN_OLD),
                OwnDriveDevice("b", "B", SEEN_NEW),
                OwnDriveDevice("c", "C", null)
            )
        )

        val ordered = GetOwnDriveDevicesUseCase(port).invoke().getOrThrow()

        assertEquals(listOf("b", "a", "c"), ordered.map { it.deviceId })
    }

    private fun entry(
        id: String,
        name: String,
        seen: Long
    ): GoogleDriveSftpRendezvousDataSource.Entry<SftpRendezvousDevice> {
        val record = mockk<SftpRendezvousDevice>(relaxed = true)
        every { record.deviceId } returns id
        every { record.deviceName } returns name
        return GoogleDriveSftpRendezvousDataSource.Entry("file-$id", "fmsx-device-$id.json", seen, record)
    }

    private companion object {
        const val SEEN_OLD = 1_000L
        const val SEEN_NEW = 2_000L
    }
}
