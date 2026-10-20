package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.data.remote.sftp.SftpChannelRacePlan.Kind
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.SftpTunnelAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class SftpChannelRacePlanTest {

    private val tunnel = HostPort(SftpTunnelAddress.host("AAAAAAAAAAAAAAAAAAAAAA", "exchange.example"), 7443)
    private val forward = HostPort("203.0.113.7", 40022)
    private val named = HostPort("home.example.org", 40022)
    private val lan = HostPort("192.168.1.5", 2222)
    private val ipv6 = HostPort("[fd00::5]", 2222)

    @Test
    fun `direct addresses go first, then the port forward, then the tunnel`() {
        val plan = SftpChannelRacePlan.plan(listOf(tunnel, forward, lan, named, ipv6))

        assertEquals(listOf(lan, ipv6, forward, named, tunnel), plan.map { it.endpoint })
        assertEquals(
            listOf(Kind.DIRECT, Kind.DIRECT, Kind.FORWARD, Kind.FORWARD, Kind.TUNNEL),
            plan.map { it.kind },
        )
    }

    @Test
    fun `attempts start 250 ms apart with the window of their kind`() {
        val plan = SftpChannelRacePlan.plan(listOf(tunnel, forward, lan))

        assertEquals(listOf(0L, 250L, 500L), plan.map { it.startDelayMs })
        assertEquals(listOf(3_000, 5_000, 10_000), plan.map { it.windowMs })
    }

    @Test
    fun `a discovered endpoint is direct whatever its address`() {
        assertEquals(Kind.DIRECT, SftpChannelRacePlan.kindOf(named, discovered = named))
    }

    @Test
    fun `private, loopback and link-local ranges are direct, the rest is a forward`() {
        listOf("10.1.2.3", "172.16.0.9", "172.31.255.1", "127.0.0.1", "169.254.3.4").forEach { host ->
            assertEquals(host, Kind.DIRECT, SftpChannelRacePlan.kindOf(HostPort(host, 22)))
        }
        listOf("172.32.0.1", "8.8.8.8", "192.169.1.1").forEach { host ->
            assertEquals(host, Kind.FORWARD, SftpChannelRacePlan.kindOf(HostPort(host, 22)))
        }
    }

    @Test
    fun `a tunnel-only record is one attempt with no delay`() {
        val plan = SftpChannelRacePlan.plan(listOf(tunnel, tunnel))

        assertEquals(1, plan.size)
        assertEquals(0L, plan.single().startDelayMs)
        assertEquals(Kind.TUNNEL, plan.single().kind)
    }
}
