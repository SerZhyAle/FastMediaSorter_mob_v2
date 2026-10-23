package com.sza.fastmediasorter.core.playback.resilience

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S4424: the user-driven edges of a radio outage, as `AudioPlaybackService` feeds them to the policy -
 * a stop or a station switch while a reconnect is pending, and catalog answers that outlive the switch.
 */
class RadioRecoveryLifecycleTest {

    @Test
    fun `a station switch mid-outage refills the ladder and closes the old reconnect window`() {
        val policy = playingStation(STATION_A)
        repeat(LADDER_LENGTH) { policy.onFailure(ioFailure(), OUTAGE_AT_MS) }

        policy.onStreamChanged(SWITCH_AT_MS, STATION_B)
        policy.onCatalogStreamPlaying(STATION_B)

        assertEquals(retryAfter(BASE_DELAY_MS), policy.onFailure(ioFailure(), SWITCH_AT_MS + YOUNG_MS))
        assertEquals(IGNORE, policy.onRetryDue(OUTAGE_AT_MS + RECONNECT_WINDOW_MS))
    }

    @Test
    fun `the previous station's late catalog answers leave the new item outside the reconnect window`() {
        val policy = playingStation(STATION_A)
        policy.onStreamChanged(SWITCH_AT_MS, STATION_B)
        policy.onKnownPlayedBefore(true, STATION_A)
        policy.onCatalogStreamPlaying(STATION_A)

        val connectWindowClosedAtMs = SWITCH_AT_MS + CONNECT_WINDOW_MS
        assertEquals(IGNORE, policy.onFailure(ioFailure(), connectWindowClosedAtMs))
        assertEquals(GIVE_UP, policy.onRetryDue(connectWindowClosedAtMs))
    }

    @Test
    fun `a retry cancelled by stop leaves no stamp, so the next outage gets the whole window`() {
        val policy = playingStation(STATION_A)
        policy.onFailure(ioFailure(), OUTAGE_AT_MS)
        policy.onRetryDropped()

        val nextOutageAtMs = OUTAGE_AT_MS + RECONNECT_WINDOW_MS
        policy.onFailure(ioFailure(), nextOutageAtMs)

        assertEquals(IGNORE, policy.onRetryDue(nextOutageAtMs + RECONNECT_WINDOW_MS - 1))
        assertEquals(GIVE_UP, policy.onRetryDue(nextOutageAtMs + RECONNECT_WINDOW_MS))
    }

    private fun playingStation(url: String): StreamServiceRetryPolicy {
        val policy = StreamServiceRetryPolicy()
        policy.onStreamChanged(CONNECTED_AT_MS, url)
        policy.onStreamReady()
        policy.onCatalogStreamPlaying(url)
        return policy
    }

    private fun ioFailure(): StreamAudioFailure {
        val code = StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        return StreamAudioFailure(StreamFailureClass.classify(code, httpStatus = null), code)
    }

    private fun retryAfter(delayMs: Long): StreamServiceRetryDecision =
        StreamServiceRetryDecision.RetryAfter(delayMs)

    private companion object {
        val IGNORE = StreamServiceRetryDecision.Ignore
        val GIVE_UP = StreamServiceRetryDecision.GiveUp

        const val CONNECT_WINDOW_MS = StreamServiceRetryPolicy.CONNECT_WINDOW_MS
        const val RECONNECT_WINDOW_MS = StreamServiceRetryPolicy.RECONNECT_WINDOW_MS
        const val BASE_DELAY_MS = StreamServiceRetryPolicy.BASE_RETRY_DELAY_MS
        const val LADDER_LENGTH = 3

        const val CONNECTED_AT_MS = 10_000L
        const val OUTAGE_AT_MS = 60_000L
        const val SWITCH_AT_MS = 70_000L
        const val YOUNG_MS = 1_000L

        const val STATION_A = "https://radio.example/a.aacp"
        const val STATION_B = "https://radio.example/b.aacp"
    }
}
