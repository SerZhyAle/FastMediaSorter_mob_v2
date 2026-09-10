package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.model.transfer.PinnedStreamsTransferPayload
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportPinnedStreamsUseCaseTest {

    private lateinit var repository: StreamSourceRepository
    private lateinit var useCase: ImportPinnedStreamsUseCase

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        useCase = ImportPinnedStreamsUseCase(repository)
    }

    @Test
    fun `payload of another kind changes nothing`() = runTest {
        val result = useCase(payload(kind = "FAVORITES"))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.add(any()) }
        coVerify(exactly = 0) { repository.reorderPinned(any()) }
    }

    @Test
    fun `payload of a newer format version changes nothing`() = runTest {
        val newer = TransferDataKind.PINNED_STREAMS.formatVersion + 1

        val result = useCase(payload(version = newer))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.add(any()) }
        coVerify(exactly = 0) { repository.reorderPinned(any()) }
    }

    @Test
    fun `unknown known-unpinned and already-pinned entries are counted apart`() = runTest {
        val known = entity(id = "known", url = "http://b/", pinned = false)
        val pinnedInPlace = entity(id = "pinned", url = "http://c/", pinned = true, sortIndex = 0)
        coEvery { repository.pinnedSnapshot() } returns listOf(pinnedInPlace)
        coEvery { repository.getByUrl("http://a/") } returns null
        coEvery { repository.getByUrl("http://b/") } returns known
        coEvery { repository.getByUrl("http://c/") } returns pinnedInPlace

        val result = useCase(
            payload(
                entries = listOf(
                    entry("http://c/", 0),
                    entry("http://a/", 1),
                    entry("http://b/", 2)
                )
            )
        )

        val report = result.getOrThrow()
        assertEquals(1, report.created)
        assertEquals(1, report.updated)
        assertEquals(1, report.skipped)
    }

    @Test
    fun `resulting pin order follows the payload order`() = runTest {
        val first = entity(id = "first", url = "http://a/", pinned = true, sortIndex = 5)
        val second = entity(id = "second", url = "http://b/", pinned = true, sortIndex = 1)
        coEvery { repository.pinnedSnapshot() } returns listOf(second, first)
        coEvery { repository.getByUrl("http://a/") } returns first
        coEvery { repository.getByUrl("http://b/") } returns second
        val ordered = slot<List<String>>()

        useCase(payload(entries = listOf(entry("http://a/", 0), entry("http://b/", 1)))).getOrThrow()

        coVerify { repository.reorderPinned(capture(ordered)) }
        assertEquals(listOf("first", "second"), ordered.captured)
    }

    private fun payload(
        kind: String = TransferDataKind.PINNED_STREAMS.name,
        version: Int = TransferDataKind.PINNED_STREAMS.formatVersion,
        entries: List<PinnedStreamsTransferPayload.PinnedStreamEntry> = listOf(entry("http://a/", 0))
    ) = PinnedStreamsTransferPayload(
        version = version,
        kind = kind,
        exportedAt = 0L,
        entries = entries
    )

    private fun entry(url: String, sortIndex: Int) =
        PinnedStreamsTransferPayload.PinnedStreamEntry(
            url = url,
            title = "title",
            mediaKind = "VIDEO",
            sortIndex = sortIndex
        )

    private fun entity(
        id: String,
        url: String,
        pinned: Boolean,
        sortIndex: Int = 0
    ) = StreamSourceEntity(
        id = id,
        url = url,
        title = "title",
        mediaKind = "VIDEO",
        sourceOrigin = "MANUAL",
        sortIndex = sortIndex,
        pinned = pinned,
        addedAt = 0L
    )
}
