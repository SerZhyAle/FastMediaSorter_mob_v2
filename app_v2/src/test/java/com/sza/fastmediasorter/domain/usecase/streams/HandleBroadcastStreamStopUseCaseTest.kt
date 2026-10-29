package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.player.AfterEndAnswer
import com.sza.fastmediasorter.domain.player.BroadcastEndDecision
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackSession
import com.sza.fastmediasorter.domain.player.BroadcastStopSignal
import com.sza.fastmediasorter.domain.usecase.streams.ResolveBroadcastPlaybackUseCase.Playback
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HandleBroadcastStreamStopUseCaseTest {

    private val check = mockk<CheckBroadcastAfterEndUseCase>()
    private val resolve = mockk<ResolveBroadcastPlaybackUseCase>()
    private val useCase = HandleBroadcastStreamStopUseCase(check, resolve)
    private val session = BroadcastPlaybackSession(
        StreamSourceEntity(
            id = "row",
            url = STORED,
            title = "Broadcast",
            mediaKind = "RTSP",
            sourceOrigin = "MANUAL",
            sortIndex = 0,
            addedAt = 0,
        ),
        broadcastId = null,
    )

    @Test
    fun aDirectoryRemovalOrARelayGone_endsWithoutAReconnect() = runTest {
        assertEquals(BroadcastEndDecision.Ended, useCase(session, BroadcastStopSignal.DirectoryRemoved))
        assertEquals(BroadcastEndDecision.Ended, useCase(session, BroadcastStopSignal.HttpStatus(GONE)))
        coVerify(exactly = 0) { check(any(), any()) }
    }

    @Test
    fun aRefusedReconnect_isAnEnd() = runTest {
        coEvery { check(STORED, null) } returns AfterEndAnswer.ENDED

        assertEquals(BroadcastEndDecision.Ended, useCase(session, BroadcastStopSignal.StreamEnded))
    }

    @Test
    fun anAnsweringBroadcast_reconnectsFromTheTop() = runTest {
        coEvery { check(STORED, null) } returns AfterEndAnswer.ALIVE
        coEvery { resolve(STORED, null, "row") } returns Playback.Playable(PLAYED)

        assertEquals(BroadcastEndDecision.Reconnect(PLAYED), useCase(session, BroadcastStopSignal.StreamEnded))
    }

    @Test
    fun anAnswerThatNoLongerResolves_orSilence_isALostConnection() = runTest {
        coEvery { check(STORED, null) } returns AfterEndAnswer.ALIVE
        coEvery { resolve(STORED, null, "row") } returns Playback.Unreachable
        assertEquals(BroadcastEndDecision.LostConnection, useCase(session, BroadcastStopSignal.HttpStatus(NOT_FOUND)))

        coEvery { check(STORED, null) } returns AfterEndAnswer.NO_ANSWER
        assertEquals(BroadcastEndDecision.LostConnection, useCase(session, BroadcastStopSignal.StreamEnded))
    }

    private companion object {
        const val STORED = "rtsp://192.168.1.20:8554/"
        const val PLAYED = "rtsp://192.168.1.20:8554/live"
        const val GONE = 410
        const val NOT_FOUND = 404
    }
}
