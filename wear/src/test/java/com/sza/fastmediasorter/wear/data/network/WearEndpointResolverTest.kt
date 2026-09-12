package com.sza.fastmediasorter.wear.data.network

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearEndpoint
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannelKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor())
        val source = makeSource(port = UNUSED_PORT, endpoints = null)

        val resolved = resolver.resolve(source)

        assertEquals(source, resolved)
    }

    @Test
    fun `an unreachable first candidate gives way to a reachable second`() = runTest {
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor())
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
        val resolver = WearEndpointResolver(monitor)
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
        val resolver = WearEndpointResolver(monitor)
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
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor())
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
    }
}
