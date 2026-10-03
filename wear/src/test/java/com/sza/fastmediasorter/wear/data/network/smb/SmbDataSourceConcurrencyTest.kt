package com.sza.fastmediasorter.wear.data.network.smb

import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.wear.data.network.WearEndpointResolver
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class SmbDataSourceConcurrencyTest {

    private val resolver = mockk<WearEndpointResolver> {
        coEvery { resolve(any()) } answers { firstArg() }
    }

    @Test
    fun `two reads that find the link dead reconnect once and close the dead link once`() {
        val opener = FakeOpener()
        val smb = SmbDataSource(resolver, opener)
        runBlocking { smb.connect(SOURCE) }
        opener.links.single().alive = false

        val results = runBlocking {
            List(READERS) { async(Dispatchers.Default) { smb.listFiles("") } }.awaitAll()
        }

        assertTrue(results.all { it.isSuccess })
        assertEquals("one reconnect for all readers", 2, opener.links.size)
        assertEquals("the dead link is closed exactly once", 1, opener.links[0].closes)
        assertEquals("the live link is never closed under a reader", 0, opener.links[1].closes)
    }

    @Test
    fun `a reconnect over a live link closes the old one before opening the next`() {
        val opener = FakeOpener()
        val smb = SmbDataSource(resolver, opener)

        runBlocking {
            smb.connect(SOURCE)
            smb.connect(SOURCE)
        }

        assertEquals(2, opener.links.size)
        assertEquals(1, opener.links[0].closes)
        assertEquals(0, opener.links[1].closes)
        assertTrue(smb.isConnected())
    }

    @Test
    fun `disconnect closes the link once and a second disconnect is a no-op`() {
        val opener = FakeOpener()
        val smb = SmbDataSource(resolver, opener)

        runBlocking {
            smb.connect(SOURCE)
            smb.disconnect()
            smb.disconnect()
        }

        assertEquals(1, opener.links.single().closes)
        assertEquals(false, smb.isConnected())
    }

    private class FakeOpener : SmbLinkOpener {
        val links = CopyOnWriteArrayList<FakeLink>()

        override suspend fun open(source: NetworkSource): SmbLink {
            // Widens the window in which a second, unserialized reconnect would slip in.
            Thread.sleep(OPEN_DELAY_MS)
            return FakeLink().also { links += it }
        }
    }

    private class FakeLink : SmbLink {
        @Volatile
        var alive = true

        @Volatile
        var closes = 0

        override val share: DiskShare = mockk(relaxed = true) {
            every { list(any<String>()) } returns emptyList()
        }

        override val isAlive: Boolean
            get() = alive

        override fun close() {
            closes++
            alive = false
        }
    }

    companion object {
        private const val READERS = 4
        private const val OPEN_DELAY_MS = 50L

        private val SOURCE = NetworkSource(
            type = NetworkSourceType.SMB,
            name = "nas",
            server = "192.0.2.10",
            username = "user",
            password = "secret",
            shareName = "media"
        )
    }
}
