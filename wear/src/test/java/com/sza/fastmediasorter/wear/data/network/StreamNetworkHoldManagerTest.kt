package com.sza.fastmediasorter.wear.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StreamNetworkHoldManagerTest {

    @Test
    fun `a block that returns normally releases the channel once`() {
        val log = RequestLog()
        val manager = StreamNetworkHoldManager(log.requester())

        val result = runBlocking { manager.withWideChannel { RESULT } }

        assertEquals(RESULT, result)
        assertEquals(1, log.requests)
        assertEquals(1, log.releases)
    }

    @Test
    fun `a block that throws releases the channel and lets the failure through`() {
        val log = RequestLog()
        val manager = StreamNetworkHoldManager(log.requester())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { manager.withWideChannel { error("stream died") } }
        }

        assertEquals(1, log.requests)
        assertEquals(1, log.releases)
    }

    @Test
    fun `a cancelled block releases the channel and lets the cancellation through`() {
        val log = RequestLog()
        val manager = StreamNetworkHoldManager(log.requester())

        assertThrows(CancellationException::class.java) {
            runBlocking { manager.withWideChannel { throw CancellationException("left the player") } }
        }

        assertEquals(1, log.requests)
        assertEquals(1, log.releases)
    }

    @Test
    fun `a nested hold releases only when the outer one finishes`() {
        val log = RequestLog()
        val manager = StreamNetworkHoldManager(log.requester())

        runBlocking {
            manager.withWideChannel {
                manager.withWideChannel { }
                assertEquals("inner exit must not release", 0, log.releases)
            }
        }

        assertEquals(1, log.requests)
        assertEquals(1, log.releases)
    }

    @Test
    fun `a request that fails leaves no phantom hold behind`() {
        val log = RequestLog()
        var failNext = true
        val requester = WideChannelRequester {
            if (failNext) {
                failNext = false
                error("platform refused the request")
            }
            log.requests++
            WideChannelHandle { log.releases++ }
        }
        val manager = StreamNetworkHoldManager(requester)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { manager.withWideChannel { } }
        }

        // The second stream must still get a real request: a counter stranded above zero by the
        // failure would make this one silently reuse a hold that was never opened.
        runBlocking { manager.withWideChannel { } }

        assertEquals(1, log.requests)
        assertEquals(1, log.releases)
    }

    private class RequestLog {
        var requests = 0
        var releases = 0

        fun requester(): WideChannelRequester = WideChannelRequester {
            requests++
            WideChannelHandle { releases++ }
        }
    }

    companion object {
        private const val RESULT = "played"
    }
}
