package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.data.files.WearServerClipboardSlot
import com.sza.fastmediasorter.wear.data.files.WearWatchFilePublisher
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardOffer
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardReceiveReport
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSaved
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSendReport
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSink
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import com.sza.fastmediasorter.wear.domain.repository.WearServerClipboardRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** S4227: where a received value lands, whether it fits, and what the watch opens. */
class GetWearServerClipboardUseCaseTest {

    private val publisher = mockk<WearWatchFilePublisher>()
    private val slot = mockk<WearServerClipboardSlot>()

    @Test
    fun `a value larger than the free space is declined and nothing is opened`() {
        every { slot.freeBytes() } returns 1
        val useCase = useCaseFor(value(WearServerClipboardKind.TEXT, null))
        every { publisher.isAvailable() } returns true

        val result = runBlocking { useCase() }

        assertTrue(result.refusedForSpace)
        assertTrue(result.report is WearServerClipboardReceiveReport.Declined)
        verify(exactly = 0) { slot.open(any()) }
    }

    @Test
    fun `a picture streams into a media row and opens from it`() {
        every { publisher.isAvailable() } returns true
        every { publisher.freeBytes() } returns Long.MAX_VALUE
        every { publisher.openPending(any(), "image/png", any()) } returns
            FakeSink(WearServerClipboardSaved("cat.png", path = null, contentUri = "content://media/1"))
        val useCase = useCaseFor(value(WearServerClipboardKind.IMAGE, "image/png"))

        val request = runBlocking { useCase() }.openRequest

        assertEquals("content://media/1", request?.contentUri)
        assertEquals("image/png", request?.mimeType)
    }

    @Test
    fun `a text lands in the slot and opens as text`() {
        every { publisher.isAvailable() } returns true
        every { slot.freeBytes() } returns Long.MAX_VALUE
        every { slot.open(any()) } returns
            FakeSink(WearServerClipboardSaved("clipboard.txt", path = "/data/clipboard.txt", contentUri = null))
        val useCase = useCaseFor(value(WearServerClipboardKind.TEXT, null))

        val request = runBlocking { useCase() }.openRequest

        assertEquals("/data/clipboard.txt", request?.path)
        assertEquals("text/plain", request?.mimeType)
    }

    @Test
    fun `an archive is saved and not opened`() {
        every { publisher.isAvailable() } returns true
        every { slot.freeBytes() } returns Long.MAX_VALUE
        every { slot.open(any()) } returns
            FakeSink(WearServerClipboardSaved("photos.zip", path = "/data/photos.zip", contentUri = null))
        val archive = value(WearServerClipboardKind.FILE, "application/zip")
        val useCase = useCaseFor(archive)

        val result = runBlocking { useCase() }

        assertTrue(result.report is WearServerClipboardReceiveReport.Received)
        assertNull(result.openRequest)
    }

    private fun useCaseFor(value: WearServerClipboardValue) =
        GetWearServerClipboardUseCase(FakeRepository(value), publisher, slot)

    private fun value(kind: WearServerClipboardKind, mimeType: String?) = WearServerClipboardValue(
        clipId = "id",
        kind = kind,
        size = 10,
        sha256 = "00",
        storedAt = "2026-10-14T20:00:00Z",
        sourceDeviceId = null,
        sourceDeviceName = "Phone",
        mimeType = mimeType,
        name = null,
    )

    private class FakeSink(private val saved: WearServerClipboardSaved) : WearServerClipboardSink {
        override val output: OutputStream = ByteArrayOutputStream()

        override fun commit(): WearServerClipboardSaved = saved

        override fun discard() = Unit
    }

    /** Plays the repository's half: asks for a sink, then reports what came of it. */
    private class FakeRepository(private val value: WearServerClipboardValue) : WearServerClipboardRepository {
        override val hasEligibleEntry: Flow<Boolean> = flowOf(true)

        override suspend fun latestState(): WearServerClipboardValue = value

        override suspend fun send(
            offer: WearServerClipboardOffer,
            source: Uri,
            onProgress: (sent: Long, total: Long) -> Unit,
        ): WearServerClipboardSendReport = WearServerClipboardSendReport(emptyList(), emptyList())

        override suspend fun receive(
            destination: suspend (WearServerClipboardValue) -> WearServerClipboardSink?,
            onProgress: (received: Long, total: Long) -> Unit,
        ): WearServerClipboardReceiveReport {
            val sink = destination(value) ?: return WearServerClipboardReceiveReport.Declined(value)
            return WearServerClipboardReceiveReport.Received(value, sink.commit()!!, "ex.example:7443", emptyList())
        }
    }
}
