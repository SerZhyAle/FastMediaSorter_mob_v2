package com.sza.fastmediasorter.domain.model

/**
 * How a tunnel candidate of contract ANYWHERE-ACCESS travels as an ordinary host:port among a
 * resource's alternate access paths: the host is `<share id>@<exchange host>` and the port is the
 * exchange server's. `@` never occurs in a hostname, so no real address reads as a tunnel, and a build
 * without tunnel support fails such a candidate fast as a host it cannot resolve.
 */
object SftpTunnelAddress {

    private const val SEPARATOR = '@'
    private const val MASK = "***"

    data class Parts(val shareId: String, val exchangeHost: String)

    /** [exchangeHost] may carry IPv6 brackets as the `FMSSFTP2` `x` field does; they are dropped here. */
    fun host(shareId: String, exchangeHost: String): String =
        shareId + SEPARATOR + exchangeHost.removePrefix("[").removeSuffix("]")

    /** Null for every host that is not a tunnel address, including one with a malformed share id. */
    fun parse(host: String): Parts? {
        val at = host.indexOf(SEPARATOR)
        val shareId = if (at > 0) host.substring(0, at) else null
        val exchangeHost = if (at > 0) host.substring(at + 1) else ""
        return if (shareId != null && SftpShareId.isValid(shareId) && exchangeHost.isNotBlank()) {
            Parts(shareId, exchangeHost)
        } else {
            null
        }
    }

    fun isTunnel(host: String): Boolean = parse(host) != null

    /**
     * [host] as a log line may print it: the share id is an access right, so a tunnel address keeps only
     * its exchange host, as the exchange server masks the id in its own logs (contract section 5.3).
     */
    fun forLog(host: String): String = parse(host)?.let { MASK + SEPARATOR + it.exchangeHost } ?: host

    /** The tunnel candidate a `FMSSFTP2` code describes, or null when it carries no server channel. */
    fun fromPairing(pairing: SftpPairingPayload): HostPort? {
        val exchange = pairing.exchangeEndpoint
        val shareId = pairing.shareId
        if (exchange == null || shareId == null) return null
        val separator = exchange.lastIndexOf(':')
        val port = exchange.substring(separator + 1).toIntOrNull()
        return port?.let { HostPort(host(shareId, exchange.substring(0, separator)), it) }
    }
}
