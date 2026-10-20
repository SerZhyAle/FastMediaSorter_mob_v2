package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.data.cloud.helpers.DriveAppDataEntry
import com.sza.fastmediasorter.data.cloud.helpers.DriveAppDataFailure
import com.sza.fastmediasorter.data.cloud.helpers.DriveAppDataListing
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.DriveSignInRequired
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * S3040/S4231: publish ordering and cleanup, listing on Drive's clock, the manifest cache and the
 * purge, against a mocked AppData data source - no network call and no Drive credential is involved.
 */
class GoogleDriveCrossDeviceTransferRepositoryTest {

    private val dataSource = mockk<GoogleDriveCrossDeviceTransferDataSource>(relaxed = true)
    private lateinit var repository: GoogleDriveCrossDeviceTransferRepositoryImpl

    private val manifest = CrossDevicePacketManifest(
        packetId = "packet1",
        createdAtEpochMs = 0L,
        senderDeviceName = "Pixel 8",
        targetDeviceName = null,
        payloadKind = CrossDevicePayloadKind.FILES,
        fileNames = listOf("clip.mp4"),
        totalSizeBytes = 3L,
        schemaVersion = CrossDevicePacketManifest.SCHEMA_V2
    )
    private val bytes = byteArrayOf(1, 2, 3)
    private val file = OutgoingPacketFile("clip.mp4", bytes.size.toLong()) { bytes.inputStream() }

    @Before
    fun setUp() {
        repository = GoogleDriveCrossDeviceTransferRepositoryImpl(dataSource)
        coEvery { dataSource.ensureQueueFolder() } returns Result.success(QUEUE_ID)
        coEvery { dataSource.ensurePacketFolder(QUEUE_ID, any()) } returns Result.success(PACKET_FOLDER)
        coEvery { dataSource.uploadPayload(any(), any(), any(), any(), any()) } coAnswers {
            arg<InputStream>(4).readBytes()
            Result.success(Unit)
        }
        coEvery { dataSource.writeManifest(any(), any()) } returns Result.success(Unit)
        coEvery { dataSource.deletePacketFolder(any()) } returns Result.success(Unit)
    }

    @Test
    fun `publish uploads the payload before the manifest and describes it with its digest`() = runTest {
        val written = slot<CrossDevicePacketManifest>()
        coEvery { dataSource.writeManifest(PACKET_FOLDER, capture(written)) } returns Result.success(Unit)

        val result = repository.publishPacket(manifest, listOf(file))

        assertTrue(result.isSuccess)
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            dataSource.uploadPayload(PACKET_FOLDER, "clip.mp4", any(), 3L, any())
            dataSource.writeManifest(PACKET_FOLDER, any())
        }
        assertEquals(SHA_OF_123, written.captured.files.single().sha256)
        coVerify(exactly = 0) { dataSource.deletePacketFolder(any()) }
    }

    @Test
    fun `a failed upload deletes its own folder and writes no manifest`() = runTest {
        coEvery { dataSource.uploadPayload(any(), any(), any(), any(), any()) } returns
            Result.failure(IOException("drop"))

        val result = repository.publishPacket(manifest, listOf(file))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dataSource.writeManifest(any(), any()) }
        coVerify(exactly = 1) { dataSource.deletePacketFolder(PACKET_FOLDER) }
    }

    @Test
    fun `listing reads each manifest once and marks old uploads without a manifest abandoned`() = runTest {
        val now = created + TimeUnit.DAYS.toMillis(EIGHT_DAYS)
        val committed = folder("f1", "packet1", created)
        val inFlight = folder("f2", "packet2", now - 1_000L)
        val abandoned = folder("f3", "packet3", created)
        val unsafe = folder("f4", "../x", created)
        coEvery { dataSource.listQueue(QUEUE_ID) } returns
            Result.success(DriveAppDataListing(listOf(committed, inFlight, abandoned, unsafe), now))
        val manifestFile = listOf(fileEntry("m1", CrossDevicePacketManifest.MANIFEST_FILE_NAME))
        coEvery { dataSource.listPacketFiles("f1") } returns Result.success(manifestFile)
        coEvery { dataSource.listPacketFiles("f2") } returns Result.success(emptyList())
        coEvery { dataSource.listPacketFiles("f3") } returns Result.success(emptyList())
        coEvery { dataSource.readManifest(manifestFile) } returns Result.success(manifest)

        val first = repository.listQueue().getOrThrow()
        repository.listQueue().getOrThrow()

        assertEquals(listOf("packet1"), first.entries.map { it.packetId })
        assertEquals(listOf("f3"), first.abandonedFolderIds)
        assertEquals(now, first.serverNowMs)
        coVerify(exactly = 1) { dataSource.readManifest(any()) }
        coVerify(exactly = 0) { dataSource.listPacketFiles("f4") }
    }

    @Test
    fun `purge deletes what is expired on drive time and the abandoned uploads`() = runTest {
        val now = created + TimeUnit.DAYS.toMillis(EIGHT_DAYS)
        val expired = CrossDevicePacketEntry(manifest, "old", created)
        val fresh = CrossDevicePacketEntry(manifest.copy(packetId = "packet2"), "new", now - 1_000L)

        val purged = repository.purgeExpired(CrossDeviceQueueSnapshot(listOf(expired, fresh), listOf("gone"), now))

        assertEquals(2, purged.getOrThrow())
        coVerify(exactly = 1) { dataSource.deletePacketFolder("old") }
        coVerify(exactly = 1) { dataSource.deletePacketFolder("gone") }
        coVerify(exactly = 0) { dataSource.deletePacketFolder("new") }
    }

    @Test
    fun `a lost sign-in surfaces as the domain failure and deletes nothing`() = runTest {
        coEvery { dataSource.listQueue(QUEUE_ID) } returns Result.failure(DriveAppDataFailure.AuthRequired())

        val result = repository.listQueue()

        assertTrue(result.exceptionOrNull() is DriveSignInRequired)
        coVerify(exactly = 0) { dataSource.deletePacketFolder(any()) }
    }

    private val created = 1_700_000_000_000L

    private fun folder(id: String, name: String, createdMs: Long) =
        DriveAppDataEntry(id, name, isFolder = true, size = 0L, createdTimeMs = createdMs, modifiedTimeMs = createdMs)

    private fun fileEntry(id: String, name: String) =
        DriveAppDataEntry(id, name, isFolder = false, size = 1L, createdTimeMs = created, modifiedTimeMs = created)

    private companion object {
        const val QUEUE_ID = "queue"
        const val PACKET_FOLDER = "packet-folder"
        const val EIGHT_DAYS = 8L
        const val SHA_OF_123 = "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"
    }
}
