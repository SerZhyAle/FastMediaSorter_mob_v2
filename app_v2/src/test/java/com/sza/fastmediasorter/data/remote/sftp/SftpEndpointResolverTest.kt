package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.ResourceType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

/**
 * S1006: verifies the reachable-endpoint fallback picks the live candidate, caches it per network,
 * re-probes after a network change, and leaves unknown single-address hosts untouched.
 */
class SftpEndpointResolverTest {

    private lateinit var reachableServer: ServerSocket
    private var reachablePort = 0
    private var deadPort = 0

    private val dao = mockk<ResourceDao>()
    private val monitor = mockk<NetworkStateMonitor>(relaxed = true)
    private val callbackSlot = slot<NetworkStateMonitor.NetworkChangeCallback>()

    @Before
    fun setUp() {
        // A live loopback listener = the reachable (alternate) endpoint.
        reachableServer = ServerSocket(0)
        reachablePort = reachableServer.localPort
        // A closed loopback port = the unreachable (primary) endpoint - connect is refused fast.
        val throwaway = ServerSocket(0)
        deadPort = throwaway.localPort
        throwaway.close()

        every { monitor.registerCallback(capture(callbackSlot)) } just Runs
    }

    @After
    fun tearDown() {
        reachableServer.close()
    }

    private fun companionResource(): ResourceEntity = ResourceEntity(
        name = "Companion",
        path = "sftp://127.0.0.1:$deadPort/Share",
        type = ResourceType.SFTP,
        altAccessPaths = "127.0.0.1:$reachablePort"
    )

    @Test
    fun `resolve picks the reachable alternate when the primary is dead`() = runBlocking {
        coEvery { dao.getAllResourcesSync() } returns listOf(companionResource())
        val resolver = SftpEndpointResolver(dao, monitor)

        val winner = resolver.resolve("127.0.0.1", deadPort)

        assertEquals(HostPort("127.0.0.1", reachablePort), winner)
    }

    @Test
    fun `second resolve is served from cache without another db read`() = runBlocking {
        coEvery { dao.getAllResourcesSync() } returns listOf(companionResource())
        val resolver = SftpEndpointResolver(dao, monitor)

        resolver.resolve("127.0.0.1", deadPort)
        resolver.resolve("127.0.0.1", deadPort)

        coVerify(exactly = 1) { dao.getAllResourcesSync() }
    }

    @Test
    fun `network change clears the cache so the next resolve re-probes`() = runBlocking {
        coEvery { dao.getAllResourcesSync() } returns listOf(companionResource())
        val resolver = SftpEndpointResolver(dao, monitor)

        resolver.resolve("127.0.0.1", deadPort)
        callbackSlot.captured.onNetworkChanged()
        resolver.resolve("127.0.0.1", deadPort)

        coVerify(exactly = 2) { dao.getAllResourcesSync() }
    }

    @Test
    fun `unknown single-address host resolves to itself without probing`() = runBlocking {
        coEvery { dao.getAllResourcesSync() } returns emptyList()
        val resolver = SftpEndpointResolver(dao, monitor)

        val winner = resolver.resolve("10.0.0.99", 22)

        assertEquals(HostPort("10.0.0.99", 22), winner)
    }
}
