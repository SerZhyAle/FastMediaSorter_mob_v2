package com.sza.fastmediasorter.ui.browse.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.usecase.transfer.SendCrossDevicePacketUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S3040: what the file sheet's send action does with a readable file, an unreadable path and a
 * refused upload - all against a mocked use case, so no Drive credential is involved.
 */
class SendToDeviceActionManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sendPacket = mockk<SendCrossDevicePacketUseCase>()
    private val manager = SendToDeviceActionManager(sendPacket)

    @Test
    fun `a readable file is sent as one media packet and reported as sent`() = runTest {
        val file = temporaryFolder.newFile("clip.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val payload = slot<Map<String, ByteArray>>()
        coEvery {
            sendPacket(CrossDevicePayloadKind.MEDIA_FILES, any(), any(), capture(payload), any())
        } returns Result.success(manifest())

        val result = manager.send(file.absolutePath, senderDeviceName = "Pixel 8")

        assertTrue(result.isSuccess)
        assertEquals(setOf("clip.mp4"), payload.captured.keys)
        assertEquals(3, payload.captured.getValue("clip.mp4").size)
        assertEquals(SendToDeviceState.Sent("clip.mp4"), manager.state.value)
    }

    @Test
    fun `a path that is not a readable file never reaches the queue`() = runTest {
        val missing = temporaryFolder.root.resolve("gone.mp4").absolutePath

        val result = manager.send(missing, senderDeviceName = "Pixel 8")

        assertTrue(result.isFailure)
        assertEquals(SendToDeviceState.Failed("gone.mp4"), manager.state.value)
        coVerify(exactly = 0) { sendPacket(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a refused upload leaves the surface in the failed state`() = runTest {
        val file = temporaryFolder.newFile("photo.jpg").apply { writeBytes(byteArrayOf(7)) }
        coEvery { sendPacket(any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("drive refused"))

        val result = manager.send(file.absolutePath, senderDeviceName = "Pixel 8")

        assertTrue(result.isFailure)
        assertEquals(SendToDeviceState.Failed("photo.jpg"), manager.state.value)
    }

    @Test
    fun `consuming the state returns the surface to idle`() = runTest {
        val file = temporaryFolder.newFile("note.txt").apply { writeBytes(byteArrayOf(1)) }
        coEvery { sendPacket(any(), any(), any(), any(), any()) } returns Result.success(manifest())

        manager.send(file.absolutePath, senderDeviceName = "Pixel 8")
        manager.consumeState()

        assertEquals(SendToDeviceState.Idle, manager.state.value)
    }

    private fun manifest() = CrossDevicePacketManifest(
        packetId = "packet-1",
        createdAtEpochMs = 1_000L,
        senderDeviceName = "Pixel 8",
        targetDeviceName = null,
        payloadKind = CrossDevicePayloadKind.MEDIA_FILES,
        fileNames = listOf("clip.mp4"),
        totalSizeBytes = 3L
    )
}
