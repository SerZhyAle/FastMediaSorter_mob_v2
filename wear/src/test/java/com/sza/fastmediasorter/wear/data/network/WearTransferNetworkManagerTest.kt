package com.sza.fastmediasorter.wear.data.network

import android.net.Network
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4227: the transfer network is lent to one block and always given back. */
class WearTransferNetworkManagerTest {

    private val network = mockk<Network>()
    private var released = 0

    private fun requesterAnswering(answer: Network?) = TransferNetworkRequester {
        object : PendingTransferNetwork {
            override suspend fun await(timeoutMs: Long): Network? = answer

            override fun release() {
                released++
            }
        }
    }

    @Test
    fun `the block runs on the granted network and the request is released`() {
        val manager = WearTransferNetworkManager(requesterAnswering(network))

        val seen = runBlocking { manager.withTransferNetwork { it } }

        assertSame(network, seen)
        assertEquals(1, released)
    }

    @Test
    fun `no network runs nothing and still releases`() {
        val manager = WearTransferNetworkManager(requesterAnswering(null))
        var ran = false

        val result = runBlocking { manager.withTransferNetwork { ran = true } }

        assertNull(result)
        assertEquals(false, ran)
        assertEquals(1, released)
    }

    @Test
    fun `a cancelled block releases the request`() {
        val manager = WearTransferNetworkManager(requesterAnswering(network))

        val cancelled = runCatching {
            runBlocking { manager.withTransferNetwork { throw CancellationException("left the screen") } }
        }

        assertTrue(cancelled.exceptionOrNull() is CancellationException)
        assertEquals(1, released)
    }
}
