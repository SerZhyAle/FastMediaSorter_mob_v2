package com.sza.fastmediasorter.data.transfer.drive

import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerNode
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelRequest
import com.sza.fastmediasorter.domain.model.transfer.DriveRefusalReason
import com.sza.fastmediasorter.domain.model.transfer.DriveReplyOutcome
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.channels.SeekableByteChannel

/** S4231: the producer's answers to `REQUEST` packets (DEVICE-EXCHANGE item CO). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveChannelResponderTest {

    private val repository = mockk<CrossDeviceTransferRepository>(relaxed = true)
    private val settings = mockk<DriveChannelSettings>(relaxed = true)
    private val served = mockk<DriveChannelServedResource>()
    private val self = LocalDeviceIdentity("dev-producer", "Pixel 8")
    private val published = mutableListOf<Pair<CrossDevicePacketManifest, List<OutgoingPacketFile>>>()

    private val camera = FakeNode(
        "Camera",
        children = listOf(FakeNode("a.jpg", size = SIX), FakeNode("b.jpg", size = SIX))
    )
    private val responder by lazy { DriveChannelResponder(repository, settings, served) }

    @Before
    fun setUp() {
        coEvery { settings.enabled } returns flowOf(true)
        coEvery { settings.packetBytes() } returns PACKET_LIMIT
        coEvery { served.resolve(RESOURCE, self.deviceId) } returns ServedResource.Served(listOf(camera))
        coEvery { repository.publishPacket(any(), any(), any()) } answers {
            published += firstArg<CrossDevicePacketManifest>() to secondArg<List<OutgoingPacketFile>>()
            Result.success(firstArg())
        }
    }

    @Test
    fun `the root lists the served folders and the request is deleted`() = runTest {
        val request = requestEntry(DriveChannelRequest.ListFolder(RESOURCE, ""))

        responder.handle(request, self, NOW)

        val (reply, files) = published.single()
        assertEquals(CrossDevicePayloadKind.REPLY, reply.payloadKind)
        assertEquals("req1", reply.inReplyTo)
        assertEquals("dev-consumer", reply.targetDeviceId)
        assertEquals(DriveReplyOutcome.OK, reply.reply?.outcome)
        assertEquals(listOf("listing.json"), files.map { it.name })
        coVerify(exactly = 1) { repository.deletePacket(request) }
    }

    @Test
    fun `a path climbing out of the root is not permitted`() = runTest {
        responder.handle(requestEntry(DriveChannelRequest.ListFolder(RESOURCE, "Camera/../..")), self, NOW)

        assertEquals(DriveRefusalReason.NOT_PERMITTED, published.single().first.reply?.reason)
    }

    @Test
    fun `a protected root answers pin-required`() = runTest {
        coEvery { served.resolve(RESOURCE, self.deviceId) } returns ServedResource.PinRequired

        responder.handle(requestEntry(DriveChannelRequest.ListFolder(RESOURCE, "")), self, NOW)

        assertEquals(DriveRefusalReason.PIN_REQUIRED, published.single().first.reply?.reason)
    }

    @Test
    fun `with the channel off nothing is served`() = runTest {
        coEvery { settings.enabled } returns flowOf(false)

        responder.handle(requestEntry(DriveChannelRequest.ListFolder(RESOURCE, "")), self, NOW)

        assertEquals(DriveRefusalReason.UNAVAILABLE, published.single().first.reply?.reason)
    }

    @Test
    fun `a file above packetBytes is too large`() = runTest {
        coEvery { settings.packetBytes() } returns 5L

        responder.handle(requestEntry(DriveChannelRequest.Fetch(RESOURCE, listOf("Camera/a.jpg"))), self, NOW)

        assertEquals(DriveRefusalReason.TOO_LARGE, published.single().first.reply?.reason)
    }

    @Test
    fun `a larger answer is split and only the last part is final`() = runTest {
        val fetch = DriveChannelRequest.Fetch(RESOURCE, listOf("Camera/a.jpg", "Camera/b.jpg"))

        responder.handle(requestEntry(fetch), self, NOW)

        assertEquals(2, published.size)
        assertEquals(listOf(false, true), published.map { it.first.reply?.final })
        assertTrue(published.all { it.second.size == 1 })
    }

    @Test
    fun `a request is answered once and only when addressed here`() = runTest {
        val request = requestEntry(DriveChannelRequest.ListFolder(RESOURCE, ""))
        val elsewhere = requestEntry(DriveChannelRequest.ListFolder(RESOURCE, ""), target = "dev-other", id = "req2")

        responder.handle(request, self, NOW)
        responder.handle(request, self, NOW)
        responder.handle(elsewhere, self, NOW)

        assertEquals(1, published.size)
    }

    private fun requestEntry(
        body: DriveChannelRequest,
        target: String = self.deviceId,
        id: String = "req1"
    ) = CrossDevicePacketEntry(
        CrossDevicePacketManifest(
            packetId = id,
            createdAtEpochMs = 0L,
            senderDeviceName = "Tablet",
            targetDeviceName = "Pixel 8",
            payloadKind = CrossDevicePayloadKind.REQUEST,
            fileNames = emptyList(),
            totalSizeBytes = 0L,
            schemaVersion = CrossDevicePacketManifest.SCHEMA_V2,
            senderDeviceId = "dev-consumer",
            targetDeviceId = target,
            request = body
        ),
        folderId = id,
        createdTimeMs = NOW
    )

    private class FakeNode(
        override val name: String,
        private val children: List<SftpServerNode>? = null,
        size: Long = 0L,
    ) : SftpServerNode {
        override val isDirectory: Boolean = children != null
        override val length: Long = size
        override val lastModified: Long = NOW
        override val canWrite: Boolean = false

        override fun listChildren(): List<SftpServerNode> = children.orEmpty()

        override fun findChild(name: String): SftpServerNode? = children?.firstOrNull { it.name == name }

        override fun createDirectory(name: String): SftpServerNode? = null

        override fun createFile(name: String): SftpServerNode? = null

        override fun delete(): Boolean = false

        override fun renameTo(newName: String): SftpServerNode? = null

        override fun moveTo(newParent: SftpServerNode): SftpServerNode? = null

        override fun openChannel(mode: SftpServerNode.ChannelMode): SeekableByteChannel =
            throw UnsupportedOperationException("the repository is mocked; no byte is read")
    }

    private companion object {
        const val RESOURCE = "res1"
        const val NOW = 1_700_000_000_000L
        const val SIX = 6L
        const val PACKET_LIMIT = 10L
    }
}
