package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPlaybackResolver
import com.sza.fastmediasorter.wear.data.exchange.WearDirectoryBroadcast
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastRoute
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A failed endpoint walks on, a reconnect starts at the top (LIVE-BROADCAST 12, DEVICE-EXCHANGE 7.7). */
class WalkWearBroadcastEndpointsUseCaseTest {

    private val record = WearDirectoryBroadcast(
        broadcastId = "b1",
        deviceId = "",
        title = "Mic",
        mode = "AUDIO_ONLY",
        descriptor = null,
        credentials = null,
    )
    private val resolver: WearBroadcastPlaybackResolver = mockk()
    private val walk = WalkWearBroadcastEndpointsUseCase(resolver)

    @Test
    fun `a failed endpoint passes the walk to the one after it`() = runBlocking {
        coEvery { resolver.resolve(record, 2) } returns playable(RELAY_URL, 2)

        val next = walk(WearBroadcastRoute(record, 1), fromTop = false)

        assertEquals(RELAY_URL, next?.url)
        assertEquals(2, next?.route?.endpointIndex)
    }

    @Test
    fun `a reconnect starts at the top of the list`() = runBlocking {
        coEvery { resolver.resolve(record, 0) } returns playable(LAN_URL, 0)

        val next = walk(WearBroadcastRoute(record, 1), fromTop = true)

        assertEquals(LAN_URL, next?.url)
    }

    @Test
    fun `an exhausted list ends the walk`() = runBlocking {
        coEvery { resolver.resolve(record, 2) } returns
            WearBroadcastPlayback.Unavailable(WearBroadcastPlayback.Reason.UNREACHABLE)

        assertNull(walk(WearBroadcastRoute(record, 1), fromTop = false))
    }

    private fun playable(url: String, index: Int) =
        WearBroadcastPlayback.Playable(url, "Mic", false, WearBroadcastRoute(record, index))

    private companion object {
        const val LAN_URL = "http://10.0.0.2:8080/"
        const val RELAY_URL = "http://127.0.0.1:4000/v2/b/b1/stream"
    }
}
