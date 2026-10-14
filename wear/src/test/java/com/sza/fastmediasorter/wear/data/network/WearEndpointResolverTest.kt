package com.sza.fastmediasorter.wear.data.network

import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntry
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearEndpoint
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannelKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

/**
 * S2488: the cache is the part that cannot be judged by reading - an entry surviving a link change
 * reproduces the dead-address symptom on a watch that has moved networks.
 */
class WearEndpointResolverTest {

    private lateinit var listening: ServerSocket
    private var reachablePort: Int = 0

    @Before
    fun setup() {
        listening = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
        reachablePort = listening.localPort
    }

    @After
    fun teardown() {
        listening.close()
    }

    @Test
    fun `a group of one is returned unchanged`() = runTest {
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor(), FakeWearExchangeEntryStore())
        val source = makeSource(port = UNUSED_PORT, endpoints = null)

        val resolved = resolver.resolve(source)

        assertEquals(source, resolved)
    }

    @Test
    fun `an unreachable first candidate gives way to a reachable second`() = runTest {
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor(), FakeWearExchangeEntryStore())
        val source = makeSource(
            port = UNUSED_PORT,
            endpoints = listOf(
                WearEndpoint(LOOPBACK, UNUSED_PORT),
                WearEndpoint(LOOPBACK, reachablePort)
            )
        )

        val resolved = resolver.resolve(source)

        assertEquals(reachablePort, resolved.port)
    }

    @Test
    fun `a second call on the same link reuses the winner`() = runTest {
        val monitor = FakeWearNetworkChannelMonitor(wifi(DOWNSTREAM_A))
        val resolver = WearEndpointResolver(monitor, FakeWearExchangeEntryStore())
        val source = makeSource(
            port = UNUSED_PORT,
            endpoints = listOf(
                WearEndpoint(LOOPBACK, UNUSED_PORT),
                WearEndpoint(LOOPBACK, reachablePort)
            )
        )
        assertEquals(reachablePort, resolver.resolve(source).port)

        // The listener is gone, so a re-probe could only fall back to the group's first candidate.
        listening.close()

        assertEquals(reachablePort, resolver.resolve(source).port)
    }

    @Test
    fun `a changed link discards the cached winner and probes again`() = runTest {
        val monitor = FakeWearNetworkChannelMonitor(wifi(DOWNSTREAM_A))
        val resolver = WearEndpointResolver(monitor, FakeWearExchangeEntryStore())
        val source = makeSource(
            port = UNUSED_PORT,
            endpoints = listOf(
                WearEndpoint(LOOPBACK, UNUSED_PORT),
                WearEndpoint(LOOPBACK, reachablePort)
            )
        )
        assertEquals(reachablePort, resolver.resolve(source).port)

        listening.close()
        monitor.set(wifi(DOWNSTREAM_B))

        assertEquals(UNUSED_PORT, resolver.resolve(source).port)
    }

    @Test
    fun `a group where nothing answers falls back to the first candidate`() = runTest {
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor(), FakeWearExchangeEntryStore())
        val source = makeSource(
            port = OTHER_UNUSED_PORT,
            endpoints = listOf(
                WearEndpoint(LOOPBACK, UNUSED_PORT),
                WearEndpoint(LOOPBACK, OTHER_UNUSED_PORT)
            )
        )

        val resolved = resolver.resolve(source)

        assertEquals(UNUSED_PORT, resolved.port)
    }

    @Test
    fun `an exchange folder answering directly takes the direct route`() = runTest {
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor(), FakeWearExchangeEntryStore(ENTRY))
        val source = makeSource(port = reachablePort, endpoints = null).bound()

        val route = resolver.route(source)

        assertTrue(route is WearSftpRoute.Direct)
        assertEquals(reachablePort, route.source.port)
    }

    @Test
    fun `an exchange folder silent at every address tunnels through the matching entry`() = runTest {
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor(), FakeWearExchangeEntryStore(ENTRY))
        val source = makeSource(port = UNUSED_PORT, endpoints = null).bound()

        val route = resolver.route(source)

        assertTrue(route is WearSftpRoute.Tunnel)
        route as WearSftpRoute.Tunnel
        assertEquals(ENTRY.identity, route.entry.identity)
        assertEquals(RESOURCE_ID, route.resourceId)
    }

    @Test
    fun `an exchange folder silent with no matching entry fails as entry missing`() = runTest {
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor(), FakeWearExchangeEntryStore())
        val source = makeSource(port = UNUSED_PORT, endpoints = null).bound()

        val failure = runCatching { resolver.route(source) }.exceptionOrNull()

        assertTrue(failure is WearExchangeEntryMissingException)
    }

    @Test
    fun `an exchange folder over the bluetooth proxy is refused before any probe`() = runTest {
        val monitor = FakeWearNetworkChannelMonitor(wifi(DOWNSTREAM_A).copy(kind = WearNetworkChannelKind.BLUETOOTH))
        val resolver = WearEndpointResolver(monitor, FakeWearExchangeEntryStore(ENTRY))
        val source = makeSource(port = reachablePort, endpoints = null).bound()

        val failure = runCatching { resolver.route(source) }.exceptionOrNull()

        assertTrue(failure is WearExchangeNeedsOwnNetworkException)
    }

    @Test
    fun `a plain source over the bluetooth proxy keeps its direct route`() = runTest {
        val monitor = FakeWearNetworkChannelMonitor(wifi(DOWNSTREAM_A).copy(kind = WearNetworkChannelKind.BLUETOOTH))
        val resolver = WearEndpointResolver(monitor, FakeWearExchangeEntryStore())
        val source = makeSource(port = reachablePort, endpoints = null)

        assertTrue(resolver.route(source) is WearSftpRoute.Direct)
    }

    private fun NetworkSource.bound() = copy(exchangeAccount = ENTRY.identity, exchangeResourceId = RESOURCE_ID)

    private fun wifi(downstreamKbps: Int) = WearNetworkChannel(
        kind = WearNetworkChannelKind.WIFI,
        downstreamKbps = downstreamKbps,
        upstreamKbps = null,
        isMetered = false,
        isValidated = true
    )

    private fun makeSource(port: Int, endpoints: List<WearEndpoint>?) = NetworkSource(
        id = "source-1",
        type = NetworkSourceType.SFTP,
        name = "Companion",
        server = LOOPBACK,
        port = port,
        username = "user",
        password = "password",
        endpoints = endpoints
    )

    private companion object {
        const val LOOPBACK = "127.0.0.1"

        /** Privileged and never bound by a test runner, so a connect here is refused at once. */
        const val UNUSED_PORT = 1
        const val OTHER_UNUSED_PORT = 2
        const val DOWNSTREAM_A = 10_000
        const val DOWNSTREAM_B = 20_000
        const val RESOURCE_ID = "res-42"
        val ENTRY = WearExchangeEntry("exchange.example", 7443, "owner", "device-1", null)
    }
}
