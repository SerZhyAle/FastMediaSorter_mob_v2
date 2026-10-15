package com.sza.fastmediasorter.data.transfer.drive

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketCodec
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketFile
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelReplyHeader
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelRequest
import com.sza.fastmediasorter.domain.model.transfer.DriveListing
import com.sza.fastmediasorter.domain.model.transfer.DriveListingEntry
import com.sza.fastmediasorter.domain.model.transfer.DriveReplyOutcome
import com.sza.fastmediasorter.domain.port.IncomingFileResult
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DrivePollState
import com.sza.fastmediasorter.domain.transfer.DriveQueueSnapshots
import com.sza.fastmediasorter.domain.transfer.DriveRequestProgress
import com.sza.fastmediasorter.domain.transfer.PollDemand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S4231: the consumer's request, its correlated replies and the 120-second give-up (DEVICE-EXCHANGE CO, CQ). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveChannelRequestClientTest {

    private val repository = mockk<CrossDeviceTransferRepository>(relaxed = true)
    private val sink = mockk<IncomingTransferFileSink>(relaxed = true)
    private val settings = mockk<DriveChannelSettings>(relaxed = true)
    private val identity = mockk<LocalDeviceIdentitySource>()
    private val self = LocalDeviceIdentity("dev-consumer", "Tablet")
    private val queue = FakeQueue()
    private var replies: (String) -> List<CrossDevicePacketEntry> = { emptyList() }
    private var requestEntry: CrossDevicePacketEntry? = null

    private val client by lazy {
        DriveChannelRequestClient(repository, sink, queue, identity, settings) { id ->
            DriveProducer(id, "Pixel 8", LAST_SEEN)
        }
    }

    @Before
    fun setUp() {
        coEvery { settings.enabled } returns flowOf(true)
        coEvery { identity.current() } returns self
        coEvery { repository.publishPacket(any(), any(), any()) } answers {
            val written = firstArg<CrossDevicePacketManifest>()
            requestEntry = CrossDevicePacketEntry(written, written.packetId, NOW)
            queue.flow.tryEmit(CrossDeviceQueueSnapshot(replies(written.packetId), emptyList(), NOW))
            Result.success(written)
        }
        coEvery { repository.listQueue() } answers {
            Result.success(CrossDeviceQueueSnapshot(listOfNotNull(requestEntry), emptyList(), NOW))
        }
        coEvery { repository.deletePacket(any()) } returns Result.success(Unit)
    }

    @Test
    fun `a listing reply is shown and deleted, and the request is left to the producer`() = runTest {
        val listing = DriveListing("2026-10-15T08:00:00Z", listOf(DriveListingEntry("a.jpg", false, 1L, "x")))
        replies = { id -> listOf(reply(id, "rep1", listOf("listing.json"), final = true)) }
        coEvery { repository.openPayload(any(), "listing.json") } answers {
            Result.success(CrossDevicePacketCodec.listingToJson(listing).byteInputStream())
        }

        val progress = client.request("dev-producer", DriveChannelRequest.ListFolder("res", "")).toList()

        assertEquals(DriveRequestProgress.Waiting("Pixel 8", LAST_SEEN), progress.first())
        assertEquals(DriveRequestProgress.Listing(listing), progress.last())
        coVerify(exactly = 1) { repository.deletePacket(match { it.packetId == "rep1" }) }
        coVerify(exactly = 0) { repository.deletePacket(match { it.packetId == requestEntry?.packetId }) }
        assertEquals(0, queue.open)
    }

    @Test
    fun `parts are taken in Drive order until the final one`() = runTest {
        replies = { id ->
            listOf(
                reply(id, "part2", listOf("b.jpg"), final = true, createdMs = NOW + 1),
                reply(id, "part1", listOf("a.jpg"), final = false, createdMs = NOW)
            )
        }
        coEvery { repository.openPayload(any(), any()) } answers { Result.success(ByteArray(1).inputStream()) }
        coEvery { sink.receive(any(), any(), any(), any()) } answers {
            IncomingFileResult.Placed("/in/${firstArg<String>()}")
        }

        val fetch = DriveChannelRequest.Fetch("res", listOf("a.jpg", "b.jpg"))

        val progress = client.request("dev-producer", fetch).toList()

        val files = progress.filterIsInstance<DriveRequestProgress.Files>()
        assertEquals(listOf(listOf("/in/a.jpg"), listOf("/in/b.jpg")), files.map { it.placed })
        assertEquals(listOf(false, true), files.map { it.final })
    }

    @Test
    fun `no reply within 120 seconds gives up and deletes the own request`() = runTest {
        val progress = client.request("dev-producer", DriveChannelRequest.ListFolder("res", "")).toList()

        assertTrue(progress.last() is DriveRequestProgress.TimedOut)
        coVerify(exactly = 1) { repository.deletePacket(match { it.packetId == requestEntry?.packetId }) }
        assertEquals(0, queue.open)
    }

    @Test
    fun `with the channel off nothing is written`() = runTest {
        coEvery { settings.enabled } returns flowOf(false)

        val progress = client.request("dev-producer", DriveChannelRequest.ListFolder("res", "")).toList()

        assertEquals(listOf(DriveRequestProgress.ChannelOff), progress)
        coVerify(exactly = 0) { repository.publishPacket(any(), any(), any()) }
    }

    private fun reply(
        requestId: String,
        id: String,
        names: List<String>,
        final: Boolean,
        createdMs: Long = NOW
    ) = CrossDevicePacketEntry(
        CrossDevicePacketManifest(
            packetId = id,
            createdAtEpochMs = 0L,
            senderDeviceName = "Pixel 8",
            targetDeviceName = self.displayName,
            payloadKind = CrossDevicePayloadKind.REPLY,
            fileNames = names,
            totalSizeBytes = names.size.toLong(),
            schemaVersion = CrossDevicePacketManifest.SCHEMA_V2,
            senderDeviceId = "dev-producer",
            targetDeviceId = self.deviceId,
            files = names.map { CrossDevicePacketFile(it, 1L, null) },
            inReplyTo = requestId,
            reply = DriveChannelReplyHeader(DriveReplyOutcome.OK, final = final, asOf = "2026-10-15T08:00:00Z")
        ),
        folderId = id,
        createdTimeMs = createdMs
    )

    private class FakeQueue : DriveQueueSnapshots {
        val flow = MutableSharedFlow<CrossDeviceQueueSnapshot>(replay = 1)
        var open = 0

        override val snapshots: SharedFlow<CrossDeviceQueueSnapshot> = flow

        override val pollState: StateFlow<DrivePollState> = MutableStateFlow(DrivePollState.Off)

        override fun acquire(demand: PollDemand): AutoCloseable {
            open++
            return AutoCloseable { open-- }
        }
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val LAST_SEEN = 1_699_999_000_000L
    }
}
