package com.sza.fastmediasorter.data.network.exceptions

import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * S0118 - narrow tests for [NetworkErrorMessageMapper.toMessageRes].
 *
 * Resource-only behavior: no Android Context needed, so these tests run as plain
 * JVM unit tests and serve as regression coverage for the friendly-copy contract
 * around network errors.
 */
class NetworkErrorMessageMapperTest {

    @Test
    fun `timeout exception maps to error_network_timeout`() {
        val res = NetworkErrorMessageMapper.toMessageRes(SocketTimeoutException("timed out"))
        assertEquals(R.string.error_network_timeout, res)
    }

    @Test
    fun `unknown host maps to connection_lost`() {
        val res = NetworkErrorMessageMapper.toMessageRes(UnknownHostException("no host"))
        assertEquals(R.string.error_network_connection_lost, res)
    }

    @Test
    fun `generic IOException maps to a non-zero resource id`() {
        // Friendly-copy regression: even unclassified IO errors must still resolve to
        // a real string resource so the user never sees an empty placeholder.
        val res = NetworkErrorMessageMapper.toMessageRes(IOException("boom"))
        assert(res != 0) { "Resource id must be non-zero" }
    }
}
