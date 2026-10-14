package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardFiles
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardGetResult
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardState
import com.sza.fastmediasorter.data.repository.ExchangeClipboardRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeClipboardEntryFailure
import com.sza.fastmediasorter.domain.model.ExchangeClipboardEntryFailure.Cause
import com.sza.fastmediasorter.domain.model.ExchangeClipboardGetOutcome
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import com.sza.fastmediasorter.domain.model.ExchangeClipboardValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** "Get the clipboard from the server" over several entries (contract DEVICE-EXCHANGE 22 items BY and CA). */
class GetClipboardFromServerUseCaseTest {

    private val repository = mockk<ExchangeClipboardRepository>()
    private val files = mockk<ExchangeClipboardFiles>(relaxed = true)
    private val useCase = GetClipboardFromServerUseCase(repository, files)
    private val incoming = File("incoming.part")
    private val placed = File("placed.txt")

    init {
        every { files.newIncomingFile() } returns incoming
        every { files.hasRoomFor(any()) } returns true
        every { files.promote(incoming, any()) } returns placed
    }

    @Test
    fun `the value stored last wins`() = runTest {
        coEvery { repository.candidates() } returns listOf(HOME, OFFICE)
        coEvery { repository.state(HOME) } returns ExchangeClipboardState.Value(value("old", EARLIER))
        coEvery { repository.state(OFFICE) } returns ExchangeClipboardState.Value(value("new", LATER))
        receives(OFFICE, "new")

        val outcome = useCase {} as ExchangeClipboardGetOutcome.Received

        assertEquals("new", outcome.value.clipId)
        assertEquals(placed, outcome.file)
    }

    @Test
    fun `on equal times the entry listed first wins`() = runTest {
        coEvery { repository.candidates() } returns listOf(HOME, OFFICE)
        coEvery { repository.state(HOME) } returns ExchangeClipboardState.Value(value("home", LATER))
        coEvery { repository.state(OFFICE) } returns ExchangeClipboardState.Value(value("office", LATER))
        receives(HOME, "home")

        val outcome = useCase {} as ExchangeClipboardGetOutcome.Received

        assertEquals("home", outcome.value.clipId)
    }

    @Test
    fun `a silent entry is skipped and named`() = runTest {
        coEvery { repository.candidates() } returns listOf(HOME, OFFICE)
        coEvery { repository.state(HOME) } coAnswers { awaitCancellation() }
        coEvery { repository.state(OFFICE) } returns ExchangeClipboardState.Value(value("office", LATER))
        receives(OFFICE, "office")

        val outcome = useCase {} as ExchangeClipboardGetOutcome.Received

        val home = ExchangeClipboardEntryFailure("home.example.org:8443", Cause.Unreachable)
        assertEquals(listOf(home), outcome.failures)
    }

    @Test
    fun `every clipboard empty is empty`() = runTest {
        coEvery { repository.candidates() } returns listOf(HOME, OFFICE)
        coEvery { repository.state(any()) } returns ExchangeClipboardState.Empty

        assertEquals(ExchangeClipboardGetOutcome.Empty(emptyList()), useCase {})
    }

    @Test
    fun `no room asks for no bytes`() = runTest {
        coEvery { repository.candidates() } returns listOf(HOME)
        coEvery { repository.state(HOME) } returns ExchangeClipboardState.Value(value("big", LATER))
        every { files.hasRoomFor(any()) } returns false

        assertEquals(ExchangeClipboardGetOutcome.NoRoom(SIZE), useCase {})
        coVerify(exactly = 0) { repository.get(any(), any(), any(), any()) }
    }

    @Test
    fun `a value replaced between the state and the get is asked for again`() = runTest {
        coEvery { repository.candidates() } returns listOf(HOME)
        coEvery { repository.state(HOME) } returnsMany listOf(
            ExchangeClipboardState.Value(value("first", EARLIER)),
            ExchangeClipboardState.Value(value("second", LATER)),
        )
        coEvery { repository.get(HOME, "first", incoming, any()) } returns
            ExchangeClipboardGetResult.Refused("unavailable")
        receives(HOME, "second")

        val outcome = useCase {} as ExchangeClipboardGetOutcome.Received

        assertEquals("second", outcome.value.clipId)
    }

    private fun receives(entry: DeviceExchangeAccountEntry, clipId: String) {
        coEvery { repository.get(entry, clipId, incoming, any()) } returns
            ExchangeClipboardGetResult.Received(value(clipId, LATER))
    }

    private fun value(clipId: String, storedAt: String) = ExchangeClipboardValue(
        clipId = clipId,
        kind = ExchangeClipboardKind.TEXT,
        size = SIZE,
        sha256 = "00",
        storedAt = storedAt,
        sourceDeviceId = null,
        sourceDeviceName = "Laptop",
        mimeType = null,
        name = null,
    )

    private companion object {
        const val SIZE = 5L
        const val EARLIER = "2026-10-14T09:00:00Z"
        const val LATER = "2026-10-14T10:00:00Z"
        val HOME = DeviceExchangeAccountEntry("home.example.org", 8443, "alice", "AAAAAAAAAAAAAAAAAAAAAA", null)
        val OFFICE = DeviceExchangeAccountEntry("office.example.net", 443, "alice", "BBBBBBBBBBBBBBBBBBBBBB", null)
    }
}
