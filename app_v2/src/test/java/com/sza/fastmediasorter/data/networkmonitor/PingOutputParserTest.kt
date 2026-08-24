package com.sza.fastmediasorter.data.networkmonitor

import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeUnavailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1617: the parser is tested against output recorded from a real device rather than a live
 * network, because the mistake this guards - a reachable host reported as dead - is made while
 * reading text, and a test that needs a network cannot be run where the parser is written.
 *
 * The samples come from the 2026-08-18 measurement recorded in strategic §6.1.
 */
class PingOutputParserTest {

    @Test
    fun `echo reply is read as reached with its round trip`() {
        val output = """
            PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
            64 bytes from 8.8.8.8: icmp_seq=1 ttl=116 time=18.4 ms

            --- 8.8.8.8 ping statistics ---
            1 packets transmitted, 1 received, 0% packet loss, time 0ms
        """.trimIndent()

        val result = PingOutputParser.parse(output)

        assertEquals(HostProbeResult.Reached(18.4, null), result)
    }

    @Test
    fun `time to live exceeded is read as a hop answering, not as the target`() {
        val output = """
            PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
            From 192.168.1.1 icmp_seq=1 Time to live exceeded

            --- 8.8.8.8 ping statistics ---
            1 packets transmitted, 0 received, +1 errors, 100% packet loss, time 0ms
        """.trimIndent()

        val result = PingOutputParser.parse(output)

        assertEquals(HostProbeResult.HopAnswered("192.168.1.1", 0.0), result)
    }

    @Test
    fun `total packet loss is read as not reached`() {
        val output = """
            PING 10.0.0.9 (10.0.0.9) 56(84) bytes of data.

            --- 10.0.0.9 ping statistics ---
            1 packets transmitted, 0 received, 100% packet loss, time 0ms
        """.trimIndent()

        val result = PingOutputParser.parse(output)

        assertEquals(HostProbeResult.NotReached, result)
    }

    @Test
    fun `unrecognised output is not measurable rather than not reached`() {
        val result = PingOutputParser.parse("ping: socket: Operation not permitted")

        val notMeasurable = result as HostProbeResult.NotMeasurable
        assertEquals(HostProbeUnavailability.MECHANISM_UNAVAILABLE, notMeasurable.cause)
    }

    @Test
    fun `empty output is not measurable`() {
        val result = PingOutputParser.parse("")

        val notMeasurable = result as HostProbeResult.NotMeasurable
        assertEquals(HostProbeUnavailability.MECHANISM_UNAVAILABLE, notMeasurable.cause)
    }

    @Test
    fun `an unresolved name is told apart from a silent host`() {
        val result = PingOutputParser.parse("ping: unknown host nosuchhost.invalid")

        val notMeasurable = result as HostProbeResult.NotMeasurable
        assertEquals(HostProbeUnavailability.NAME_NOT_RESOLVED, notMeasurable.cause)
    }

    @Test
    fun `an absent network is told apart from a silent host`() {
        val result = PingOutputParser.parse("connect: Network is unreachable")

        val notMeasurable = result as HostProbeResult.NotMeasurable
        assertEquals(HostProbeUnavailability.NO_NETWORK, notMeasurable.cause)
    }

    @Test
    fun `a hop that also reports a time keeps the hop reading`() {
        val output = "From 192.168.1.1 icmp_seq=1 Time to live exceeded time=2.11 ms"

        val result = PingOutputParser.parse(output)

        assertTrue("expected a hop, got $result", result is HostProbeResult.HopAnswered)
        assertEquals(2.11, (result as HostProbeResult.HopAnswered).roundTripMillis, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001
    }
}
