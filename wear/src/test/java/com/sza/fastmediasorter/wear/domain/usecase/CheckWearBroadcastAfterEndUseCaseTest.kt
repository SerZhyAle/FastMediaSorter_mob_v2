package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastAfterEndProbe
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastCredentials
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastEndpoint
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastEndpoints
import com.sza.fastmediasorter.wear.data.exchange.WearDirectoryBroadcast
import com.sza.fastmediasorter.wear.domain.playback.WearAfterEndAnswer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** DEVICE-EXCHANGE item DY: the watch's one after-end reconnect over its LAN and relay endpoints. */
class CheckWearBroadcastAfterEndUseCaseTest {

    private val lan = WearBroadcastEndpoint.Lan("http://10.0.0.2:8080/", "10.0.0.2", LAN_PORT, MODE)
    private val relay = WearBroadcastEndpoint.Relay("relay.example", HTTPS_PORT, "/v2/b/b1/stream", null, MODE)
    private val probe: WearBroadcastAfterEndProbe = mockk()
    private val check = CheckWearBroadcastAfterEndUseCase(probe)

    @Before
    fun setUp() {
        mockkObject(WearBroadcastEndpoints)
        every { WearBroadcastEndpoints.parse(any(), any(), any(), any()) } returns listOf(lan, relay)
        every { WearBroadcastEndpoints.isPlayableMode(any()) } returns true
        every { WearBroadcastEndpoints.isProtected(any()) } returns false
    }

    @After
    fun tearDown() {
        unmockkObject(WearBroadcastEndpoints)
    }

    @Test
    fun `an ended LAN and a silent relay answer ended`() = runTest {
        every { probe.lan(lan) } returns WearAfterEndAnswer.ENDED
        every { probe.relay(relay, null) } returns WearAfterEndAnswer.NO_ANSWER

        assertEquals(WearAfterEndAnswer.ENDED, check(record(null)))
    }

    @Test
    fun `an alive relay outranks an ended LAN`() = runTest {
        every { probe.lan(lan) } returns WearAfterEndAnswer.ENDED
        every { probe.relay(relay, null) } returns WearAfterEndAnswer.ALIVE

        assertEquals(WearAfterEndAnswer.ALIVE, check(record(null)))
    }

    @Test
    fun `silence everywhere is no answer`() = runTest {
        every { probe.lan(lan) } returns WearAfterEndAnswer.NO_ANSWER
        every { probe.relay(relay, null) } returns WearAfterEndAnswer.NO_ANSWER

        assertEquals(WearAfterEndAnswer.NO_ANSWER, check(record(null)))
    }

    @Test
    fun `a protected broadcast asks only the relay, with its header`() = runTest {
        every { WearBroadcastEndpoints.isProtected(any()) } returns true
        every { probe.relay(relay, any()) } returns WearAfterEndAnswer.ENDED

        assertEquals(WearAfterEndAnswer.ENDED, check(record(WearBroadcastCredentials("u", "p"))))
        verify(exactly = 0) { probe.lan(any()) }
        verify { probe.relay(relay, match { it.startsWith("Authorization: Basic ") }) }
    }

    @Test
    fun `a protected broadcast without credentials asks nothing`() = runTest {
        every { WearBroadcastEndpoints.isProtected(any()) } returns true

        assertEquals(WearAfterEndAnswer.NO_ANSWER, check(record(null)))
        verify(exactly = 0) { probe.lan(any()) }
        verify(exactly = 0) { probe.relay(any(), any()) }
    }

    private fun record(credentials: WearBroadcastCredentials?) = WearDirectoryBroadcast(
        broadcastId = "b1",
        deviceId = "",
        title = "Mic",
        mode = MODE,
        descriptor = null,
        credentials = credentials,
    )

    private companion object {
        const val MODE = "AUDIO_ONLY"
        const val LAN_PORT = 8080
        const val HTTPS_PORT = 443
    }
}
