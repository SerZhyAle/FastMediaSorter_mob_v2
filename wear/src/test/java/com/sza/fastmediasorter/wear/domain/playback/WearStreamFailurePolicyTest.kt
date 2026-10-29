package com.sza.fastmediasorter.wear.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * S4473: the line between a producer that stopped and a source that failed. A wrong ENDED tells the wearer
 * a broadcast is gone when the watch only lost its Wi-Fi; a wrong FAILED hides a stop behind a retry.
 */
class WearStreamFailurePolicyTest {

    private val refused = ConnectException(
        "failed to connect to /192.168.1.5 (port 8080): ECONNREFUSED (Connection refused)"
    )

    @Test
    fun `404 means the stream is gone`() {
        assertEquals(WearStreamFailure.ENDED, WearStreamFailurePolicy.ofError(404, null))
    }

    @Test
    fun `410 means the stream is gone`() {
        assertEquals(WearStreamFailure.ENDED, WearStreamFailurePolicy.ofError(410, null))
    }

    @Test
    fun `another status is a failure`() {
        assertEquals(WearStreamFailure.FAILED, WearStreamFailurePolicy.ofError(500, null))
        assertEquals(WearStreamFailure.FAILED, WearStreamFailurePolicy.ofError(403, null))
    }

    @Test
    fun `a refused connection means the producer closed its port`() {
        assertEquals(WearStreamFailure.ENDED, WearStreamFailurePolicy.ofError(null, refused))
    }

    @Test
    fun `a refusal buried in the cause chain is found`() {
        val wrapped = IOException("source error", IOException("open failed", refused))
        assertEquals(WearStreamFailure.ENDED, WearStreamFailurePolicy.ofError(null, wrapped))
    }

    @Test
    fun `a timeout is a failure not an end`() {
        assertEquals(
            WearStreamFailure.FAILED,
            WearStreamFailurePolicy.ofError(null, SocketTimeoutException("timeout"))
        )
    }

    @Test
    fun `an unreachable network is a failure not an end`() {
        val unreachable = ConnectException("connect failed: ENETUNREACH (Network is unreachable)")
        assertEquals(WearStreamFailure.FAILED, WearStreamFailurePolicy.ofError(null, unreachable))
    }

    @Test
    fun `no status and no cause is a failure`() {
        assertEquals(WearStreamFailure.FAILED, WearStreamFailurePolicy.ofError(null, null))
    }

    @Test
    fun `a refusal deeper than the bound is not followed`() {
        var chain: Throwable = refused
        repeat(DEEPER_THAN_BOUND) { chain = IOException("wrapper", chain) }
        assertEquals(WearStreamFailure.FAILED, WearStreamFailurePolicy.ofError(null, chain))
    }

    private companion object {
        /** Past the policy's depth bound of ten, so the refusal at the bottom is out of reach. */
        const val DEEPER_THAN_BOUND = 12
    }
}
