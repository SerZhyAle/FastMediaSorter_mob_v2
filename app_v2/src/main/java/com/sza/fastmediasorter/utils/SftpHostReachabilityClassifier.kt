package com.sza.fastmediasorter.utils

/**
 * S0984: classifies an SFTP host string for the "share access" export warning, using only the
 * literal address - no DNS resolution or any other network call (export must not touch the wire).
 *
 * A [PRIVATE_LAN] host means a recipient outside the sender's network cannot connect; the export
 * dialog surfaces that honestly. [HOSTNAME] is "unknown routability" (a DNS name we do not resolve).
 */
object SftpHostReachabilityClassifier {

    enum class Reachability { PRIVATE_LAN, PUBLIC_ROUTABLE, HOSTNAME }

    fun classify(host: String): Reachability {
        val trimmed = host.trim().lowercase()
        if (trimmed.isEmpty()) return Reachability.HOSTNAME
        if (trimmed.endsWith(MDNS_SUFFIX)) return Reachability.PRIVATE_LAN

        val octets = parseIpv4(trimmed)
        if (octets != null) {
            return if (isPrivateIpv4(octets)) Reachability.PRIVATE_LAN else Reachability.PUBLIC_ROUTABLE
        }
        if (trimmed.contains(':')) return classifyIpv6(trimmed)
        return Reachability.HOSTNAME
    }

    private fun parseIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != OCTET_COUNT) return null
        val octets = IntArray(OCTET_COUNT)
        for (i in parts.indices) {
            val value = parts[i].toIntOrNull() ?: return null
            if (value < OCTET_MIN || value > OCTET_MAX) return null
            octets[i] = value
        }
        return octets
    }

    private fun isPrivateIpv4(octets: IntArray): Boolean {
        val first = octets[0]
        val second = octets[1]
        return when {
            first == PRIVATE_10 -> true
            first == LOOPBACK_127 -> true
            first == PRIVATE_172 && second in PRIVATE_172_LOW..PRIVATE_172_HIGH -> true
            first == PRIVATE_192 && second == PRIVATE_192_SECOND -> true
            first == CGNAT_100 && second in CGNAT_LOW..CGNAT_HIGH -> true
            first == LINKLOCAL_169 && second == LINKLOCAL_254 -> true
            else -> false
        }
    }

    private fun classifyIpv6(host: String): Reachability {
        val bare = host.trim('[', ']')
        val isPrivate = bare == IPV6_LOOPBACK ||
            IPV6_PRIVATE_PREFIXES.any { bare.startsWith(it) }
        return if (isPrivate) Reachability.PRIVATE_LAN else Reachability.PUBLIC_ROUTABLE
    }

    private const val MDNS_SUFFIX = ".local"
    private const val OCTET_COUNT = 4
    private const val OCTET_MIN = 0
    private const val OCTET_MAX = 255
    private const val PRIVATE_10 = 10
    private const val PRIVATE_172 = 172
    private const val PRIVATE_172_LOW = 16
    private const val PRIVATE_172_HIGH = 31
    private const val PRIVATE_192 = 192
    private const val PRIVATE_192_SECOND = 168
    private const val CGNAT_100 = 100
    private const val CGNAT_LOW = 64
    private const val CGNAT_HIGH = 127
    private const val LOOPBACK_127 = 127
    private const val LINKLOCAL_169 = 169
    private const val LINKLOCAL_254 = 254
    private const val IPV6_LOOPBACK = "::1"
    // fe8..feb covers link-local fe80::/10; fc/fd cover unique-local fc00::/7.
    private val IPV6_PRIVATE_PREFIXES = listOf("fe8", "fe9", "fea", "feb", "fc", "fd")
}
