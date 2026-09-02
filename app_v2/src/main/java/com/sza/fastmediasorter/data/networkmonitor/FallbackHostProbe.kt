package com.sza.fastmediasorter.data.networkmonitor

import com.sza.fastmediasorter.domain.networkmonitor.HostProbe
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import javax.inject.Inject

/**
 * S1617: the composite every caller actually gets - spawned `ping` first, TCP connect only when
 * ping could not run.
 *
 * The fallback is reached on "could not measure" and on nothing else. A host that genuinely did not
 * answer the echo must not be re-probed over TCP and then reported as reachable, because those are
 * answers to two different questions.
 */
class FallbackHostProbe @Inject constructor(
    private val systemPing: SystemPingHostProbe,
    private val tcpConnect: TcpConnectHostProbe,
) : HostProbe {

    override suspend fun probe(host: String, timeoutMillis: Long, ttl: Int?): HostProbeResult {
        val primary = systemPing.probe(host, timeoutMillis, ttl)
        return when (primary) {
            is HostProbeResult.NotMeasurable -> tcpConnect.probe(host, timeoutMillis, ttl)
            else -> primary
        }
    }
}
