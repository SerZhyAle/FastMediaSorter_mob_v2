package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastEndpointProbe
import com.sza.fastmediasorter.domain.player.AfterEndAnswer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckBroadcastAfterEndUseCaseTest {

    private fun probe(lan: AfterEndAnswer, relay: AfterEndAnswer, tunnel: AfterEndAnswer = AfterEndAnswer.NO_ANSWER) =
        mockk<BroadcastEndpointProbe>().also {
            every { it.lanAfterEnd(any()) } returns lan
            every { it.relayAfterEnd(any()) } returns relay
            every { it.tunnelAfterEnd(any()) } returns tunnel
        }

    private suspend fun ask(probe: BroadcastEndpointProbe, endpoints: String?) =
        CheckBroadcastAfterEndUseCase(probe, Dispatchers.Unconfined)(LAN_URL, endpoints)

    @Test
    fun anAnsweringLan_beatsAnEndedRelayAndStopsTheWalk() = runTest {
        val probe = probe(lan = AfterEndAnswer.ALIVE, relay = AfterEndAnswer.ENDED)

        assertEquals(AfterEndAnswer.ALIVE, ask(probe, ALL))
        verify(exactly = 0) { probe.relayAfterEnd(any()) }
    }

    @Test
    fun anEndedRelay_beatsASilentLan() = runTest {
        val probe = probe(lan = AfterEndAnswer.NO_ANSWER, relay = AfterEndAnswer.ENDED)

        assertEquals(AfterEndAnswer.ENDED, ask(probe, ALL))
    }

    @Test
    fun silenceEverywhere_isNoAnswer() = runTest {
        assertEquals(AfterEndAnswer.NO_ANSWER, ask(probe(AfterEndAnswer.NO_ANSWER, AfterEndAnswer.NO_ANSWER), ALL))
    }

    @Test
    fun aRowWithoutAList_asksItsOwnAddress() = runTest {
        val probe = probe(lan = AfterEndAnswer.ENDED, relay = AfterEndAnswer.ALIVE)

        assertEquals(AfterEndAnswer.ENDED, ask(probe, null))
        verify(exactly = 1) { probe.lanAfterEnd(LAN_URL) }
    }

    private companion object {
        const val LAN_URL = "rtsp://192.168.1.20:8554/"
        const val BROADCAST_ID = "Zm9vYmFyYmF6cXV4MTIzNA"
        const val RELAY_URL = "https://exchange.example.org/v2/b/$BROADCAST_ID/stream"
        val ALL: String? = BroadcastEndpointCodec.encode(
            listOf(
                BroadcastEndpointDto(url = LAN_URL, transport = "RTSP"),
                BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY"),
                BroadcastEndpointDto(
                    url = "fmsx://exchange.example.org/b/$BROADCAST_ID/rtsp",
                    transport = "TUNNEL",
                    inner = LAN_URL,
                ),
            )
        )
    }
}
