package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.SftpTunnelAddress

/**
 * The order and the timing of the race over one record's channels (contract DEVICE-EXCHANGE item BB): direct
 * addresses first, then the port forward, then the exchange tunnel; each attempt starts [STAGGER_MS] after the
 * previous one and runs side by side with it under a window of its own kind, so one dead address does not eat
 * the budget of the next. The windows are the contract's working defaults.
 */
object SftpChannelRacePlan {

    enum class Kind(val windowMs: Int) {
        DIRECT(DIRECT_WINDOW_MS),
        FORWARD(FORWARD_WINDOW_MS),
        TUNNEL(TUNNEL_WINDOW_MS),
    }

    data class Attempt(val endpoint: HostPort, val kind: Kind, val startDelayMs: Long) {
        val windowMs: Int get() = kind.windowMs
    }

    /** [discovered] is the endpoint found on the LAN for the record's host key; it is direct by definition. */
    fun plan(candidates: List<HostPort>, discovered: HostPort? = null): List<Attempt> =
        candidates.distinct()
            .map { it to kindOf(it, discovered) }
            .sortedBy { (_, kind) -> kind.ordinal }
            .mapIndexed { index, (endpoint, kind) -> Attempt(endpoint, kind, index * STAGGER_MS) }

    fun kindOf(endpoint: HostPort, discovered: HostPort? = null): Kind = when {
        SftpTunnelAddress.isTunnel(endpoint.host) -> Kind.TUNNEL
        endpoint == discovered || isLocalLiteral(endpoint.host) -> Kind.DIRECT
        else -> Kind.FORWARD
    }

    // A host name or a public IPv4 address is what a port forward is written as; an IPv6 literal is a direct
    // address of the device (contract item BB names "the LAN and IPv6 hosts of `h`" as the direct channel).
    private fun isLocalLiteral(host: String): Boolean {
        val bare = host.removePrefix("[").removeSuffix("]")
        return bare.contains(':') || isPrivateIpv4(bare)
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val octets = host.split('.').mapNotNull { it.toIntOrNull()?.takeIf { value -> value in 0..MAX_OCTET } }
        if (octets.size != IPV4_OCTETS) return false
        val (first, second) = octets
        return first == TEN || first == LOOPBACK ||
            (first == B_FIRST && second in B_SECOND_MIN..B_SECOND_MAX) ||
            (first == C_FIRST && second == C_SECOND) ||
            (first == LINK_LOCAL_FIRST && second == LINK_LOCAL_SECOND)
    }

    const val STAGGER_MS = 250L
    private const val DIRECT_WINDOW_MS = 3_000
    private const val FORWARD_WINDOW_MS = 5_000
    private const val TUNNEL_WINDOW_MS = 10_000
    private const val MAX_OCTET = 255
    private const val IPV4_OCTETS = 4
    private const val TEN = 10
    private const val LOOPBACK = 127
    private const val B_FIRST = 172
    private const val B_SECOND_MIN = 16
    private const val B_SECOND_MAX = 31
    private const val C_FIRST = 192
    private const val C_SECOND = 168
    private const val LINK_LOCAL_FIRST = 169
    private const val LINK_LOCAL_SECOND = 254
}
