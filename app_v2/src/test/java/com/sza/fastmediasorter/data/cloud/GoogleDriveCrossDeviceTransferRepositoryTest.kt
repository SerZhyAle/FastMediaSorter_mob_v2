package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketStatus
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S3040: publish ordering, pending filtering, claim branches and TTL purge, against a mocked
 * AppData data source - no network call and no Drive credential is involved.
 */
class GoogleDriveCrossDeviceTransferRepositoryTest {

    private val dataSource = mockk<GoogleDriveCrossDeviceTransferDataSource>(relaxed = true)
    private lateinit var repository: GoogleDriveCrossDeviceTransferRepositoryImpl

    private val manifest = CrossDevicePacketManifest(
        packetId = "packet-1",
        createdAtEpochMs = System.currentTimeMillis(),
        senderDeviceName = "Pixel 8",
        targetDeviceName = null,
        payloadKind = CrossDevicePayloadKind.MEDIA_FILES,
        fileNames = listOf("clip.mp4"),
        totalSizeBytes = 4L
    )

    @Before
    fun setUp() {
        repository = GoogleDriveCrossDeviceTransferRepositoryImpl(dataSource)
        coEvery { dataSource.ensureQueueFolder() } returns Result.success(QUEUE_ID)
        coEvery { dataSource.ensurePacketFolder(QUEUE_ID, any()) } returns Result.success(PACKET_ID)
        coEvery { dataSource.uploadPayloadFile(any(), any(), any()) } returns Result.success(Unit)
        coEvery { dataSource.writeManifest(any(), any()) } returns Result.success(Unit)
        coEvery { dataSource.deletePacketFolder(any()) } returns Result.success(Unit)
    }

    @Test
    fun `publish uploads the payload before the manifest`() = runTest {
        val result = repository.publishPacket(manifest, mapOf("clip.mp4" to byteArrayOf(1, 2, 3, 4)))

        assertTrue(result.isSuccess)
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            dataSource.uploadPayloadFile(PACKET_ID, "clip.mp4", any())
            dataSource.writeManifest(PACKET_ID, manifest)
        }
    }

    @Test
    fun `pending list drops claimed and expired packets`() = runTest {
        val claimed = manifest.copy(packetId = "packet-2", status = CrossDevicePacketStatus.CLAIMED)
        val expired = manifest.copy(
            packetId = "packet-3",
            createdAtEpochMs = System.currentTimeMillis() - EIGHT_DAYS_MS
        )
        coEvery { dataSource.listPacketFolders(QUEUE_ID) } returns Result.success(
            listOf(folder("f1", manifest.packetId), folder("f2", claimed.packetId), folder("f3", expired.packetId))
        )
        coEvery { dataSource.readManifest("f1") } returns Result.success(manifest)
        coEvery { dataSource.readManifest("f2") } returns Result.success(claimed)
        coEvery { dataSource.readManifest("f3") } returns Result.success(expired)

        val pending = repository.listPendingPackets().getOrThrow()

        assertEquals(listOf(manifest), pending)
    }

    @Test
    fun `accept and delete removes the packet folder`() = runTest {
        coEvery { dataSource.listPacketFolders(QUEUE_ID) } returns
            Result.success(listOf(folder(PACKET_ID, manifest.packetId)))

        val result = repository.claimPacket(manifest.packetId, deleteFromCloud = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { dataSource.deletePacketFolder(PACKET_ID) }
        coVerify(exactly = 0) { dataSource.writeManifest(PACKET_ID, any()) }
    }

    @Test
    fun `accept without deletion rewrites the manifest as claimed`() = runTest {
        coEvery { dataSource.listPacketFolders(QUEUE_ID) } returns
            Result.success(listOf(folder(PACKET_ID, manifest.packetId)))
        coEvery { dataSource.readManifest(PACKET_ID) } returns Result.success(manifest)

        val result = repository.claimPacket(manifest.packetId, deleteFromCloud = false)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            dataSource.writeManifest(PACKET_ID, manifest.copy(status = CrossDevicePacketStatus.CLAIMED))
        }
        coVerify(exactly = 0) { dataSource.deletePacketFolder(any()) }
    }

    @Test
    fun `fetching a packet that left the queue fails instead of hanging`() = runTest {
        coEvery { dataSource.listPacketFolders(QUEUE_ID) } returns Result.success(emptyList())

        val result = repository.fetchPayload(manifest.packetId, "clip.mp4")

        assertTrue(result.isFailure)
    }

    @Test
    fun `purge deletes only packets past the age limit`() = runTest {
        val stale = manifest.copy(
            packetId = "packet-old",
            createdAtEpochMs = System.currentTimeMillis() - EIGHT_DAYS_MS
        )
        coEvery { dataSource.listPacketFolders(QUEUE_ID) } returns Result.success(
            listOf(folder("fresh", manifest.packetId), folder("stale", stale.packetId))
        )
        coEvery { dataSource.readManifest("fresh") } returns Result.success(manifest)
        coEvery { dataSource.readManifest("stale") } returns Result.success(stale)

        val purged = repository.purgeExpiredPackets(SEVEN_DAYS_MS).getOrThrow()

        assertEquals(1, purged)
        coVerify(exactly = 1) { dataSource.deletePacketFolder("stale") }
    }

    @Test
    fun `an orphan folder with no manifest survives until its own folder date ages out`() = runTest {
        coEvery { dataSource.listPacketFolders(QUEUE_ID) } returns Result.success(
            listOf(folder("orphan", "packet-orphan", modifiedDate = System.currentTimeMillis()))
        )
        coEvery { dataSource.readManifest("orphan") } returns Result.success(null)

        val purged = repository.purgeExpiredPackets(SEVEN_DAYS_MS).getOrThrow()

        assertEquals(0, purged)
        coVerify(exactly = 0) { dataSource.deletePacketFolder("orphan") }
    }

    private fun folder(id: String, name: String, modifiedDate: Long = 0L): CloudFile =
        CloudFile(
            id = id,
            name = name,
            path = "/$name",
            isFolder = true,
            modifiedDate = modifiedDate
        )

    companion object {
        private const val QUEUE_ID = "queue-folder"
        private const val PACKET_ID = "packet-folder"
        private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
        private const val EIGHT_DAYS_MS = 8L * 24L * 60L * 60L * 1000L
    }
}
