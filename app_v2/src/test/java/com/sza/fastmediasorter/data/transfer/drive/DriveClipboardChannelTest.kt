package com.sza.fastmediasorter.data.transfer.drive

import com.sza.fastmediasorter.domain.model.transfer.ClipKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelOff
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
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
import java.util.concurrent.TimeUnit

/** S4231: the clipboard value by Drive time and the 10-minute cleanup grace (DEVICE-EXCHANGE item CP). */
class DriveClipboardChannelTest {

    private val repository = mockk<CrossDeviceTransferRepository>(relaxed = true)
    private val settings = mockk<DriveChannelSettings>(relaxed = true)
    private val identity = mockk<LocalDeviceIdentitySource>()
    private val sink = mockk<IncomingTransferFileSink>(relaxed = true)
    private val channel by lazy { DriveClipboardChannel(repository, settings, identity, sink) }

    @Before
    fun setUp() {
        coEvery { settings.enabled } returns flowOf(true)
        coEvery { settings.clipboardEnabled } returns flowOf(true)
        coEvery { identity.current() } returns LocalDeviceIdentity("dev", "Phone")
        coEvery { repository.deletePacket(any()) } returns Result.success(Unit)
    }

    @Test
    fun `the latest drive time wins whatever the writers' clocks said`() = runTest {
        val skewedNewer = clip("a", driveMs = NOW, writerClockMs = 0L)
        val skewedOlder = clip("b", driveMs = NOW - 1, writerClockMs = Long.MAX_VALUE / 2)

        val value = DriveClipboardChannel.valueOf(snapshot(skewedOlder, skewedNewer))

        assertEquals("a", value?.packetId)
    }

    @Test
    fun `cleanup keeps the value and anything inside the grace`() = runTest {
        val value = clip("value", driveMs = NOW)
        val recent = clip("recent", driveMs = NOW - TimeUnit.MINUTES.toMillis(FIVE))
        val old = clip("old", driveMs = NOW - TimeUnit.MINUTES.toMillis(ELEVEN))

        channel.cleanup(snapshot(value, recent, old))

        coVerify(exactly = 1) { repository.deletePacket(old) }
        coVerify(exactly = 0) { repository.deletePacket(value) }
        coVerify(exactly = 0) { repository.deletePacket(recent) }
    }

    @Test
    fun `the clipboard needs its own option`() = runTest {
        coEvery { settings.clipboardEnabled } returns flowOf(false)

        val result = channel.put(ClipKind.TEXT, "x", "text/plain", 1L) { ByteArray(1).inputStream() }

        assertTrue(result.exceptionOrNull() is DriveChannelOff)
        coVerify(exactly = 0) { repository.publishPacket(any(), any(), any()) }
    }

    @Test
    fun `a text value is written as clipboard txt`() = runTest {
        coEvery { repository.publishPacket(any(), any(), any()) } answers { Result.success(firstArg()) }
        coEvery { repository.listQueue() } returns Result.success(snapshot())

        channel.put(ClipKind.TEXT, "ignored.txt", "text/plain", 1L) { ByteArray(1).inputStream() }

        coVerify { repository.publishPacket(match { it.fileNames == listOf("clipboard.txt") }, any(), any()) }
    }

    private fun snapshot(vararg entries: CrossDevicePacketEntry) =
        CrossDeviceQueueSnapshot(entries.toList(), emptyList(), NOW)

    private fun clip(id: String, driveMs: Long, writerClockMs: Long = 0L) = CrossDevicePacketEntry(
        CrossDevicePacketManifest(
            packetId = id,
            createdAtEpochMs = writerClockMs,
            senderDeviceName = "Phone",
            targetDeviceName = null,
            payloadKind = CrossDevicePayloadKind.CLIPBOARD,
            fileNames = listOf("clipboard.txt"),
            totalSizeBytes = 1L,
            schemaVersion = CrossDevicePacketManifest.SCHEMA_V2
        ),
        folderId = id,
        createdTimeMs = driveMs
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val FIVE = 5L
        const val ELEVEN = 11L
    }
}
