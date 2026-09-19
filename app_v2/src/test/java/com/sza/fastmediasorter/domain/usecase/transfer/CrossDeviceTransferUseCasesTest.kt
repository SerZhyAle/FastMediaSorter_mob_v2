package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTransferOption
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.model.transfer.TransferReport
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3040: the four cross-device use cases - manifest composition on send, visibility filtering on
 * list, both accept branches on receive, and the TTL argument the cleanup passes down.
 */
class CrossDeviceTransferUseCasesTest {

    private val repository = mockk<CrossDeviceTransferRepository>(relaxed = true)
    private val applyPayload = mockk<ApplyTransferPayloadUseCase>(relaxed = true)
    private val fileSink = mockk<IncomingTransferFileSink>(relaxed = true)

    private val mediaManifest = CrossDevicePacketManifest(
        packetId = "packet-media",
        createdAtEpochMs = 1_000L,
        senderDeviceName = "Pixel 8",
        targetDeviceName = null,
        payloadKind = CrossDevicePayloadKind.MEDIA_FILES,
        fileNames = listOf("clip.mp4"),
        totalSizeBytes = 3L
    )

    @Test
    fun `send composes a manifest from the payload it was handed`() = runTest {
        val captured = slot<CrossDevicePacketManifest>()
        coEvery { repository.publishPacket(capture(captured), any()) } returns Result.success(Unit)

        val result = SendCrossDevicePacketUseCase(repository).invoke(
            payloadKind = CrossDevicePayloadKind.MEDIA_FILES,
            senderDeviceName = "Pixel 8",
            targetDeviceName = "Living Room TV",
            payloadBytes = mapOf("a.mp4" to byteArrayOf(1, 2), "b.jpg" to byteArrayOf(3))
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("a.mp4", "b.jpg"), captured.captured.fileNames)
        assertEquals(3L, captured.captured.totalSizeBytes)
        assertEquals(CrossDevicePacketManifest.DEFAULT_TTL_DAYS, captured.captured.ttlDays)
        assertTrue("packetId must be unique per send", captured.captured.packetId.isNotEmpty())
    }

    @Test
    fun `pending list hides this device's own sends and packets aimed elsewhere`() = runTest {
        val ownSend = mediaManifest.copy(packetId = "own", senderDeviceName = LOCAL_DEVICE)
        val forOther = mediaManifest.copy(packetId = "other", targetDeviceName = "Tablet")
        val forThisDevice = mediaManifest.copy(packetId = "mine", targetDeviceName = LOCAL_DEVICE)
        coEvery { repository.listPendingPackets() } returns
            Result.success(listOf(ownSend, forOther, forThisDevice, mediaManifest))

        val visible = GetPendingCrossDevicePacketsUseCase(repository).invoke(LOCAL_DEVICE).getOrThrow()

        assertEquals(setOf("mine", "packet-media"), visible.map { it.packetId }.toSet())
    }

    @Test
    fun `receive and keep claims without deleting from the cloud`() = runTest {
        coEvery { repository.fetchPayload(any(), any()) } returns Result.success(byteArrayOf(1, 2, 3))
        coEvery { fileSink.write("clip.mp4", any()) } returns "/storage/incoming/clip.mp4"

        val outcome = receiveUseCase().invoke(mediaManifest, CrossDeviceTransferOption.ACCEPT).getOrThrow()

        assertEquals(listOf("/storage/incoming/clip.mp4"), outcome.writtenPaths)
        assertFalse(outcome.settingsApplied)
        coVerify(exactly = 1) { repository.claimPacket("packet-media", deleteFromCloud = false) }
    }

    @Test
    fun `receive and delete claims with the cloud deletion flag`() = runTest {
        coEvery { repository.fetchPayload(any(), any()) } returns Result.success(byteArrayOf(1))
        coEvery { fileSink.write(any(), any()) } returns "/storage/incoming/clip.mp4"

        receiveUseCase().invoke(mediaManifest, CrossDeviceTransferOption.ACCEPT_AND_DELETE).getOrThrow()

        coVerify(exactly = 1) { repository.claimPacket("packet-media", deleteFromCloud = true) }
    }

    @Test
    fun `a settings packet is applied through the shipped transfer importer`() = runTest {
        val settingsManifest = mediaManifest.copy(
            packetId = "packet-settings",
            payloadKind = CrossDevicePayloadKind.SETTINGS,
            fileNames = listOf("fms_settings.json")
        )
        coEvery { repository.fetchPayload(any(), any()) } returns Result.success(byteArrayOf(9))
        coEvery { applyPayload(TransferDataKind.SETTINGS, any()) } returns
            Result.success(TransferReport(TransferDataKind.SETTINGS, created = 1, updated = 0, skipped = 0))

        val outcome = receiveUseCase().invoke(settingsManifest, CrossDeviceTransferOption.ACCEPT).getOrThrow()

        assertTrue(outcome.settingsApplied)
        coVerify(exactly = 1) { applyPayload(TransferDataKind.SETTINGS, any()) }
        coVerify(exactly = 0) { fileSink.write(any(), any()) }
    }

    @Test
    fun `a packet whose files cannot be stored is not claimed`() = runTest {
        coEvery { repository.fetchPayload(any(), any()) } returns Result.success(byteArrayOf(1))
        coEvery { fileSink.write(any(), any()) } returns null

        val result = receiveUseCase().invoke(mediaManifest, CrossDeviceTransferOption.ACCEPT_AND_DELETE)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.claimPacket(any(), any()) }
    }

    @Test
    fun `cleanup purges with the ttl expressed in milliseconds`() = runTest {
        coEvery { repository.purgeExpiredPackets(any()) } returns Result.success(2)

        val purged = CleanExpiredCrossDevicePacketsUseCase(repository).invoke().getOrThrow()

        assertEquals(2, purged)
        coVerify(exactly = 1) { repository.purgeExpiredPackets(SEVEN_DAYS_MS) }
    }

    private fun receiveUseCase() = ReceiveCrossDevicePacketUseCase(repository, applyPayload, fileSink)

    private companion object {
        const val LOCAL_DEVICE = "Living Room TV"
        const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
