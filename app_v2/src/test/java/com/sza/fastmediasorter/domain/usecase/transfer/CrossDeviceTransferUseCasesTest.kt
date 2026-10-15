package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketStatus
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTarget
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTransferOption
import com.sza.fastmediasorter.domain.model.transfer.DriveDailyLimit
import com.sza.fastmediasorter.domain.model.transfer.DriveQueueFull
import com.sza.fastmediasorter.domain.model.transfer.DriveQuota
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.model.transfer.PacketIntegrityFailure
import com.sza.fastmediasorter.domain.model.transfer.PacketTooLarge
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.model.transfer.TransferReport
import com.sza.fastmediasorter.domain.port.IncomingFileResult
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * S3040/S4231: send limits and manifest composition, the queue as one device sees it, receipt with its
 * integrity check and target-aware deletion, and the cleanup's snapshot hand-off.
 */
class CrossDeviceTransferUseCasesTest {

    private val repository = mockk<CrossDeviceTransferRepository>(relaxed = true)
    private val applyPayload = mockk<ApplyTransferPayloadUseCase>(relaxed = true)
    private val fileSink = mockk<IncomingTransferFileSink>(relaxed = true)
    private val settings = mockk<DriveChannelSettings>(relaxed = true)

    private val self = LocalDeviceIdentity(deviceId = "dev-self", displayName = LOCAL_DEVICE)
    private val now = 1_700_000_000_000L
    private val mediaManifest = CrossDevicePacketManifest(
        packetId = "packetmedia",
        createdAtEpochMs = 1_000L,
        senderDeviceName = "Pixel 8",
        targetDeviceName = null,
        payloadKind = CrossDevicePayloadKind.MEDIA_FILES,
        fileNames = listOf("clip.mp4"),
        totalSizeBytes = 3L
    )
    private val file = OutgoingPacketFile("a.mp4", 3L) { byteArrayOf(1, 2, 3).inputStream() }

    @Before
    fun setUp() {
        coEvery { settings.packetBytes() } returns PACKET_LIMIT
        coEvery { settings.receivedPacketIds() } returns emptySet()
        coEvery { repository.readStorageQuota() } returns Result.success(DriveQuota(limitBytes = null, usageBytes = 0L))
        coEvery { repository.publishPacket(any(), any(), any()) } answers { Result.success(firstArg()) }
    }

    @Test
    fun `send composes a version 2 manifest addressed by device id`() = runTest {
        val captured = slot<CrossDevicePacketManifest>()
        coEvery { repository.publishPacket(capture(captured), any(), any()) } answers { Result.success(firstArg()) }

        val result = sendUseCase().invoke(
            CrossDevicePayloadKind.FILES,
            self,
            CrossDeviceTarget("dev-tv", "Living Room TV"),
            listOf(file)
        )

        assertTrue(result.isSuccess)
        val manifest = captured.captured
        assertEquals(CrossDevicePacketManifest.SCHEMA_V2, manifest.schemaVersion)
        assertEquals("dev-self", manifest.senderDeviceId)
        assertEquals("dev-tv", manifest.targetDeviceId)
        assertEquals("Living Room TV", manifest.targetDeviceName)
        assertFalse("packet id is a Drive-safe share id", manifest.packetId.contains('-'))
    }

    @Test
    fun `a packet above packetBytes is refused before any upload`() = runTest {
        coEvery { settings.packetBytes() } returns 2L

        val result = sendUseCase().invoke(CrossDevicePayloadKind.FILES, self, null, listOf(file))

        assertTrue(result.exceptionOrNull() is PacketTooLarge)
        coVerify(exactly = 0) { repository.publishPacket(any(), any(), any()) }
    }

    @Test
    fun `a packet without one percent to spare is refused before any upload`() = runTest {
        val nearlyFull = DriveQuota(limitBytes = 100L, usageBytes = 97L)
        coEvery { repository.readStorageQuota() } returns Result.success(nearlyFull)

        val result = sendUseCase().invoke(CrossDevicePayloadKind.FILES, self, null, listOf(file))

        assertTrue(result.exceptionOrNull() is DriveQueueFull)
        assertEquals(3L, (result.exceptionOrNull() as DriveQueueFull).freeBytes)
        coVerify(exactly = 0) { repository.publishPacket(any(), any(), any()) }
    }

    @Test
    fun `the daily limit is reported once and not retried`() = runTest {
        coEvery { repository.publishPacket(any(), any(), any()) } returns Result.failure(DriveDailyLimit())

        val result = sendUseCase().invoke(CrossDevicePayloadKind.FILES, self, null, listOf(file))

        assertTrue(result.exceptionOrNull() is DriveDailyLimit)
        coVerify(exactly = 1) { repository.publishPacket(any(), any(), any()) }
    }

    @Test
    fun `the queue view offers what is for this device and keeps own sends apart`() = runTest {
        val ownSend = entry(mediaManifest.copy(packetId = "own", senderDeviceId = "dev-self"))
        val forOther = entry(mediaManifest.copy(packetId = "other", targetDeviceId = "dev-tablet"))
        val forMe = entry(mediaManifest.copy(packetId = "mine", targetDeviceId = "dev-self"))
        val claimed = entry(mediaManifest.copy(packetId = "claimed", status = CrossDevicePacketStatus.CLAIMED))
        val request = entry(mediaManifest.copy(packetId = "req", payloadKind = CrossDevicePayloadKind.REQUEST))
        val clip = entry(mediaManifest.copy(packetId = "clip", payloadKind = CrossDevicePayloadKind.CLIPBOARD))
        val eightDaysAgo = now - TimeUnit.DAYS.toMillis(EIGHT_DAYS)
        val expired = CrossDevicePacketEntry(mediaManifest.copy(packetId = "old"), "old", eightDaysAgo)
        val received = entry(mediaManifest.copy(packetId = "done"))
        val snapshot = CrossDeviceQueueSnapshot(
            listOf(ownSend, forOther, forMe, claimed, request, clip, expired, received),
            emptyList(),
            now
        )

        val view = GetPendingCrossDevicePacketsUseCase.viewOf(snapshot, self, setOf("done"))

        assertEquals(setOf("mine", "claimed"), view.incoming.map { it.packetId }.toSet())
        assertEquals(listOf("own"), view.outgoing.map { it.packetId })
    }

    @Test
    fun `a targeted packet is deleted by its target after receipt`() = runTest {
        stubPlaced()

        val targeted = entry(mediaManifest.copy(targetDeviceId = "dev-self"))

        receiveUseCase().invoke(targeted, self, CrossDeviceTransferOption.ACCEPT).getOrThrow()

        coVerify(exactly = 1) { repository.deletePacket(any()) }
        coVerify(exactly = 1) { settings.markReceived("packetmedia") }
    }

    @Test
    fun `an untargeted packet stays for the other devices`() = runTest {
        stubPlaced()

        receiveUseCase().invoke(entry(mediaManifest), self, CrossDeviceTransferOption.ACCEPT).getOrThrow()

        coVerify(exactly = 0) { repository.deletePacket(any()) }
    }

    @Test
    fun `receive and delete removes an untargeted packet on request`() = runTest {
        stubPlaced()

        receiveUseCase().invoke(entry(mediaManifest), self, CrossDeviceTransferOption.ACCEPT_AND_DELETE).getOrThrow()

        coVerify(exactly = 1) { repository.deletePacket(any()) }
    }

    @Test
    fun `a file failing its check discards what was placed and keeps the packet`() = runTest {
        val twoFiles = mediaManifest.copy(fileNames = listOf("a.jpg", "b.jpg"), targetDeviceId = "dev-self")
        coEvery { repository.openPayload(any(), any()) } answers { Result.success(byteArrayOf(1).inputStream()) }
        coEvery { fileSink.receive("a.jpg", any(), any(), any()) } returns IncomingFileResult.Placed("/in/a.jpg")
        coEvery { fileSink.receive("b.jpg", any(), any(), any()) } returns IncomingFileResult.Rejected("digest")

        val result = receiveUseCase().invoke(entry(twoFiles), self, CrossDeviceTransferOption.ACCEPT)

        assertTrue(result.exceptionOrNull() is PacketIntegrityFailure)
        coVerify(exactly = 1) { fileSink.discard("/in/a.jpg") }
        coVerify(exactly = 0) { repository.deletePacket(any()) }
    }

    @Test
    fun `a settings packet is applied through the shipped transfer importer`() = runTest {
        val settingsManifest = mediaManifest.copy(
            packetId = "packetsettings",
            payloadKind = CrossDevicePayloadKind.SETTINGS,
            fileNames = listOf("fms_settings.json")
        )
        coEvery { repository.openPayload(any(), any()) } answers { Result.success(byteArrayOf(9).inputStream()) }
        coEvery { applyPayload(TransferDataKind.SETTINGS, any()) } returns
            Result.success(TransferReport(TransferDataKind.SETTINGS, created = 1, updated = 0, skipped = 0))

        val outcome = receiveUseCase()
            .invoke(entry(settingsManifest), self, CrossDeviceTransferOption.ACCEPT)
            .getOrThrow()

        assertTrue(outcome.settingsApplied)
        coVerify(exactly = 0) { fileSink.receive(any(), any(), any(), any()) }
    }

    @Test
    fun `cleanup purges the snapshot it listed`() = runTest {
        val snapshot = CrossDeviceQueueSnapshot(emptyList(), listOf("gone"), now)
        coEvery { repository.listQueue() } returns Result.success(snapshot)
        coEvery { repository.purgeExpired(snapshot) } returns Result.success(1)

        assertEquals(1, CleanExpiredCrossDevicePacketsUseCase(repository).invoke().getOrThrow())
    }

    private fun stubPlaced() {
        coEvery { repository.openPayload(any(), any()) } answers { Result.success(byteArrayOf(1, 2, 3).inputStream()) }
        coEvery { fileSink.receive(any(), any(), any(), any()) } returns IncomingFileResult.Placed("/in/clip.mp4")
        coEvery { repository.deletePacket(any()) } returns Result.success(Unit)
    }

    private fun entry(manifest: CrossDevicePacketManifest) = CrossDevicePacketEntry(manifest, manifest.packetId, now)

    private fun sendUseCase() = SendCrossDevicePacketUseCase(repository, settings)

    private fun receiveUseCase() = ReceiveCrossDevicePacketUseCase(repository, applyPayload, fileSink, settings)

    private companion object {
        const val LOCAL_DEVICE = "Living Room TV"
        const val PACKET_LIMIT = 1_000L
        const val EIGHT_DAYS = 8L
    }
}
