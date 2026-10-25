package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.model.PreparedLinkChoice
import com.sza.fastmediasorter.domain.repository.PreparedBroadcastLinkStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckPreparedBroadcastStartUseCaseTest {

    private val entry = DeviceExchangeAccountEntry("exchange.example", 7443, "alice", "DEVICE_A", null, outgoing = true)
    private val store = mockk<PreparedBroadcastLinkStore>()
    private val accounts = mockk<DeviceExchangeAccountRepository> { every { producer } returns flowOf(entry) }
    private val check = CheckPreparedBroadcastStartUseCase(store, accounts)

    @Test
    fun `nothing prepared lets every start through`() = runTest {
        coEvery { store.all() } returns emptyList()
        assertEquals(CheckPreparedBroadcastStartUseCase.Verdict.NothingPrepared, check(BroadcastMode.AUDIO_ONLY))
    }

    @Test
    fun `the prepared mode on the same entry matches and another mode will not work`() = runTest {
        coEvery { store.all() } returns listOf(link("AUDIO_ONLY"))
        assertEquals(CheckPreparedBroadcastStartUseCase.Verdict.Matches, check(BroadcastMode.AUDIO_ONLY))
        assertEquals(CheckPreparedBroadcastStartUseCase.Verdict.WillNotWork, check(BroadcastMode.VIDEO_ONLY))
    }

    @Test
    fun `a file start with an unknown mode matches any link of the entry`() = runTest {
        coEvery { store.all() } returns listOf(link("VIDEO_AUDIO"))
        assertEquals(CheckPreparedBroadcastStartUseCase.Verdict.Matches, check(null))
    }

    private fun link(mode: String) = PreparedBroadcastLink(
        broadcastId = "b1",
        entryIdentity = entry.identity,
        publicEndpoint = "exchange.example:7443",
        certFingerprint = null,
        mode = mode,
        title = "Phone",
        sourceId = null,
        createdAtMs = 0L,
        untilMs = Long.MAX_VALUE,
        linkChoice = PreparedLinkChoice.OPEN,
        linkUser = null,
        linkPassword = null,
        relayLinkKey = null,
        tunnelAlias = null,
        tunnelPin = null,
    )
}
