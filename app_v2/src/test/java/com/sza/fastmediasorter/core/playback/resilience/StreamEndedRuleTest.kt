package com.sza.fastmediasorter.core.playback.resilience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamEndedRuleTest {

    @Test
    fun `a live stream that played and is not a broadcast reconnects`() {
        assertEquals(StreamEndedOutcome.RECONNECT, decide(isBroadcast = false, hasPlayed = true, live = true))
    }

    @Test
    fun `a broadcast row never reconnects`() {
        assertEquals(StreamEndedOutcome.SETTLE, decide(isBroadcast = true, hasPlayed = true, live = true))
    }

    @Test
    fun `a source that never played settles`() {
        assertEquals(StreamEndedOutcome.SETTLE, decide(isBroadcast = false, hasPlayed = false, live = true))
    }

    @Test
    fun `a finite file settles when its end arrives`() {
        assertEquals(StreamEndedOutcome.SETTLE, decide(isBroadcast = false, hasPlayed = true, live = false))
    }

    @Test
    fun `exactly one of the eight input combinations reconnects`() {
        val booleans = listOf(false, true)
        val reconnecting = booleans.flatMap { broadcast ->
            booleans.flatMap { played -> booleans.map { live -> Triple(broadcast, played, live) } }
        }.filter { (broadcast, played, live) ->
            decide(broadcast, played, live) == StreamEndedOutcome.RECONNECT
        }

        assertEquals(listOf(Triple(false, true, true)), reconnecting)
    }

    @Test
    fun `the lost connection is the classified connection-failed error`() {
        val lost = StreamEndedRule.CONNECTION_LOST

        assertEquals(StreamFailureClass.TRANSIENT_NETWORK, lost.failureClass)
        assertEquals(StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, lost.errorCode)
        assertEquals(
            StreamFailureClass.TRANSIENT_NETWORK,
            StreamFailureClass.classify(lost.errorCode, httpStatus = null),
        )
    }

    @Test
    fun `both audio ladders spend a retry on the lost connection`() {
        assertTrue(StreamEndedRule.CONNECTION_LOST.isRetryableAtAudioSite())
    }

    private fun decide(isBroadcast: Boolean, hasPlayed: Boolean, live: Boolean): StreamEndedOutcome =
        StreamEndedRule.decide(
            isBroadcast = isBroadcast,
            hasPlayed = hasPlayed,
            wasLiveOrDurationUnknown = live,
        )
}
