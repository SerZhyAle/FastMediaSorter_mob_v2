package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackSessions
import com.sza.fastmediasorter.domain.usecase.streams.ResolveBroadcastPlaybackUseCase.Playback
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ResolvePlayerStreamUseCaseTest {
    private val getSource = mockk<GetStreamSourceByUrlUseCase>()
    private val resolve = mockk<ResolveBroadcastPlaybackUseCase>()
    private val useCase = ResolvePlayerStreamUseCase(getSource, resolve)
    private val source = StreamSourceEntity(
        id = "selected-row",
        url = STORED,
        title = "Broadcast",
        mediaKind = "VIDEO",
        sourceOrigin = "IMPORTED",
        sortIndex = 0,
        addedAt = 0L,
        broadcastEndpoints = "[]",
    )

    @After
    fun clearSession() = BroadcastPlaybackSessions.clear(PLAYED)

    @Test
    fun plainAndAdHocUrlsPassThroughWithoutResolution() = runTest {
        coEvery { getSource(STORED) } returns null
        assertEquals(Playback.Playable(STORED), useCase(STORED))
        coEvery { getSource(STORED) } returns source.copy(broadcastEndpoints = null)
        assertEquals(Playback.Playable(STORED), useCase(STORED))
        coVerify(exactly = 0) { resolve(any(), any(), any()) }
    }

    @Test
    fun everySelectionResolvesTheStoredEndpointsAndRegistersThePlayedSession() = runTest {
        coEvery { getSource(STORED) } returns source
        coEvery { resolve(STORED, source.broadcastEndpoints, source.id) } returns Playback.Playable(PLAYED)
        assertEquals(Playback.Playable(PLAYED), useCase(STORED))
        assertSame(source, BroadcastPlaybackSessions.forUrl(PLAYED)?.source)
        assertEquals(Playback.Playable(PLAYED), useCase(STORED))
        coVerify(exactly = 2) { resolve(STORED, source.broadcastEndpoints, source.id) }
    }

    @Test
    fun resolutionFailuresAreReturnedWithoutStartingARawSession() = runTest {
        coEvery { getSource(STORED) } returns source
        val failures = listOf(
            Playback.TrustRefused("server"),
            Playback.Unreachable,
            Playback.NotStarted(Long.MAX_VALUE),
            Playback.Expired,
        )
        for (failure in failures) {
            coEvery { resolve(STORED, source.broadcastEndpoints, source.id) } returns failure
            assertEquals(failure, useCase(STORED))
            assertNull(BroadcastPlaybackSessions.forUrl(PLAYED))
            assertNull(BroadcastPlaybackSessions.forUrl(STORED))
        }
    }

    @Test
    fun cancelledSelectionCannotRegisterEvenIfTheResolverReturnsAfterCancellation() = runTest {
        coEvery { getSource(STORED) } returns source
        coEvery { resolve(STORED, source.broadcastEndpoints, source.id) } coAnswers {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                Playback.Playable(PLAYED)
            }
        }
        val selection = launch { useCase(STORED) }
        runCurrent()
        selection.cancelAndJoin()
        assertNull(BroadcastPlaybackSessions.forUrl(PLAYED))
    }

    private companion object {
        const val STORED = "rtsp://192.0.2.1:8554/live"
        const val PLAYED = "http://127.0.0.1:12345/selected-broadcast"
    }
}
