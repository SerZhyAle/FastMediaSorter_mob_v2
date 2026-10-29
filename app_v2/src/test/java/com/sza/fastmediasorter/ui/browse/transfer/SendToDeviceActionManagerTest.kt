package com.sza.fastmediasorter.ui.browse.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DriveFileChannel
import com.sza.fastmediasorter.domain.usecase.transfer.GetOwnDriveDevicesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S3040/S4231: what the file sheet's send action does with a readable file, an unreadable path and a
 * refused upload - all against a mocked use case, so no Drive credential is involved.
 */
class SendToDeviceActionManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val channel = mockk<DriveFileChannel>()
    private val manager = SendToDeviceActionManager(
        channel,
        mockk<DriveChannelSettings>(relaxed = true),
        mockk<GetOwnDriveDevicesUseCase>(relaxed = true),
        mockk(),
        Dispatchers.Unconfined
    )

    @Test
    fun `a readable file is streamed as one packet and reported as sent`() = runTest {
        val file = temporaryFolder.newFile("clip.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val files = slot<List<OutgoingPacketFile>>()
        coEvery {
            channel.sendFiles(any(), capture(files), CrossDevicePayloadKind.FILES)
        } returns Result.success(manifest())

        val result = manager.send(file.absolutePath)

        assertTrue(result.isSuccess)
        val sent = files.captured.single()
        assertEquals("clip.mp4", sent.name)
        assertEquals(3L, sent.size)
        assertEquals(3, sent.open().use { it.readBytes() }.size)
        assertEquals(SendToDeviceState.Sent("clip.mp4"), manager.state.value)
    }

    @Test
    fun `a path that is not a readable file never reaches the queue`() = runTest {
        val missing = temporaryFolder.root.resolve("gone.mp4").absolutePath

        val result = manager.send(missing)

        assertTrue(result.isFailure)
        assertEquals(SendToDeviceState.Failed("gone.mp4"), manager.state.value)
        coVerify(exactly = 0) { channel.sendFiles(any(), any(), any()) }
    }

    @Test
    fun `a refused upload leaves the surface in the failed state`() = runTest {
        val file = temporaryFolder.newFile("photo.jpg").apply { writeBytes(byteArrayOf(7)) }
        coEvery { channel.sendFiles(any(), any(), any()) } returns
            Result.failure(IllegalStateException("drive refused"))

        val result = manager.send(file.absolutePath)

        assertTrue(result.isFailure)
        assertEquals(SendToDeviceState.Failed("photo.jpg"), manager.state.value)
    }

    @Test
    fun `consuming the state returns the surface to idle`() = runTest {
        val file = temporaryFolder.newFile("note.txt").apply { writeBytes(byteArrayOf(1)) }
        coEvery { channel.sendFiles(any(), any(), any()) } returns Result.success(manifest())

        manager.send(file.absolutePath)
        manager.consumeState()

        assertEquals(SendToDeviceState.Idle, manager.state.value)
    }

    private fun manifest() = CrossDevicePacketManifest(
        packetId = "packet1",
        createdAtEpochMs = 1_000L,
        senderDeviceName = "Pixel 8",
        targetDeviceName = null,
        payloadKind = CrossDevicePayloadKind.MEDIA_FILES,
        fileNames = listOf("clip.mp4"),
        totalSizeBytes = 3L
    )
}
