package com.sza.fastmediasorter.domain.model

import java.util.Locale

/**
 * The `x` of a connection string (contract DEVICE-EXCHANGE 5.5): an exchange server `host:port`.
 *
 * [host] is held in its written form - a DNS name in lower case, an IPv4 dotted quad, or an IPv6 literal
 * without brackets in RFC 5952 form - so [toString] is the canonical text and two spellings of one server
 * compare equal.
 */
data class ExchangeEndpoint(val host: String, val port: Int) {

    val isIpv6: Boolean get() = ':' in host

    override fun toString(): String = if (isIpv6) "[$host]:$port" else "$host:$port"

    companion object {
        private const val MAX_PORT = 65_535
        private const val MAX_PORT_DIGITS = 5
        private const val MAX_NAME_LENGTH = 253
        private const val MAX_LABEL_LENGTH = 63
        private const val IPV4_PARTS = 4
        private const val MAX_OCTET = 255
        private const val MAX_OCTET_DIGITS = 3

        /** Null for anything that is not a `host:port` of the contract's form; never resolves a name. */
        fun parse(value: String): ExchangeEndpoint? {
            val separator = value.lastIndexOf(':')
            val rawHost = if (separator > 0) value.substring(0, separator) else ""
            val port = if (separator > 0) parsePort(value.substring(separator + 1)) else null
            val host = when {
                rawHost.length > 2 && rawHost.startsWith('[') && rawHost.endsWith(']') ->
                    Ipv6Literal.canonical(rawHost.substring(1, rawHost.length - 1))
                else -> parsePlainHost(rawHost)
            }
            return if (host != null && port != null) ExchangeEndpoint(host, port) else null
        }

        // A leading zero is tolerated and dropped; a sign or a sixth digit is not a port.
        private fun parsePort(text: String): Int? = text
            .takeIf { it.length in 1..MAX_PORT_DIGITS && it.all(::isAsciiDigit) }
            ?.toInt()
            ?.takeIf { it in 1..MAX_PORT }

        // A name made only of digits and dots is read as IPv4 or refused, never as a DNS name.
        private fun parsePlainHost(text: String): String? = when {
            text.isEmpty() -> null
            text.all { isAsciiDigit(it) || it == '.' } -> parseIpv4(text)
            else -> parseDnsName(text)
        }

        internal fun parseIpv4(text: String): String? = text.split('.')
            .takeIf { parts -> parts.size == IPV4_PARTS && parts.all(::isOctet) }
            ?.joinToString(".")

        private fun isOctet(part: String): Boolean =
            part.length in 1..MAX_OCTET_DIGITS && part.all(::isAsciiDigit) &&
                (part.length == 1 || part[0] != '0') && part.toInt() <= MAX_OCTET

        private fun parseDnsName(text: String): String? = text
            .takeIf { it.length <= MAX_NAME_LENGTH && it.split('.').all(::isLabel) }
            ?.lowercase(Locale.ROOT)

        private fun isLabel(label: String): Boolean =
            label.length in 1..MAX_LABEL_LENGTH &&
                label.first() != '-' && label.last() != '-' &&
                label.all { isAsciiDigit(it) || it in 'a'..'z' || it in 'A'..'Z' || it == '-' }

        private fun isAsciiDigit(char: Char): Boolean = char in '0'..'9'
    }
}

/** An IPv6 address literal without brackets or zone, read in any RFC 4291 text form and written per RFC 5952. */
internal object Ipv6Literal {
    private const val GROUPS = 8
    private const val MAX_GROUP_DIGITS = 4
    private const val HEX_RADIX = 16
    private const val MAPPED_PREFIX_GROUPS = 5
    private const val MAPPED_MARKER = 0xFFFF
    private const val BYTE_BITS = 8
    private const val BYTE_MASK = 0xFF
    private const val IPV4_GROUPS = 2

    fun canonical(text: String): String? = parse(text)?.let(::format)

    private fun parse(text: String): IntArray? {
        val halves = text.split("::")
        if (halves.size == 2 && '.' in halves[0]) return null
        val head = halves.firstOrNull()?.let(::groups)
        val tail = if (halves.size == 2) groups(halves[1]) else emptyList()
        val groupCount = (head?.size ?: 0) + (tail?.size ?: 0)
        val complete = when (halves.size) {
            1 -> groupCount == GROUPS
            2 -> groupCount < GROUPS
            else -> false
        }
        return if (complete && head != null && tail != null) {
            (head + List(GROUPS - groupCount) { 0 } + tail).toIntArray()
        } else {
            null
        }
    }

    // An embedded dotted quad (`::ffff:192.0.2.1`) stands for the last two groups.
    private fun groups(part: String): List<Int>? {
        if (part.isEmpty()) return emptyList()
        val tokens = part.split(':')
        val parsed = tokens.mapIndexed { index, token ->
            if (index == tokens.lastIndex && '.' in token) ipv4Groups(token) else hexGroup(token)?.let(::listOf)
        }
        return if (parsed.any { it == null }) null else parsed.flatMap { it.orEmpty() }
    }

    private fun hexGroup(token: String): Int? = token
        .takeIf { it.length in 1..MAX_GROUP_DIGITS && it.all(::isHexDigit) }
        ?.toInt(HEX_RADIX)

    private fun isHexDigit(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

    private fun ipv4Groups(token: String): List<Int>? = ExchangeEndpoint.parseIpv4(token)
        ?.split('.')
        ?.map(String::toInt)
        ?.chunked(IPV4_GROUPS) { (high, low) -> (high shl BYTE_BITS) or low }

    private fun format(groups: IntArray): String {
        val mapped = groups[MAPPED_PREFIX_GROUPS] == MAPPED_MARKER &&
            (0 until MAPPED_PREFIX_GROUPS).all { groups[it] == 0 }
        return if (mapped) {
            val quad = groups.drop(MAPPED_PREFIX_GROUPS + 1)
                .flatMap { listOf(it shr BYTE_BITS, it and BYTE_MASK) }
                .joinToString(".")
            "::ffff:$quad"
        } else {
            compress(groups.map { Integer.toHexString(it) })
        }
    }

    // RFC 5952 4.2: the longest run of two or more zero groups becomes `::`, the first one on a tie.
    private fun compress(hex: List<String>): String {
        var bestStart = -1
        var bestLength = 1
        var runStart = -1
        hex.forEachIndexed { index, group ->
            if (group == "0") {
                if (runStart < 0) runStart = index
                val length = index - runStart + 1
                if (length > bestLength) {
                    bestStart = runStart
                    bestLength = length
                }
            } else {
                runStart = -1
            }
        }
        return if (bestStart < 0) {
            hex.joinToString(":")
        } else {
            hex.subList(0, bestStart).joinToString(":") + "::" +
                hex.subList(bestStart + bestLength, hex.size).joinToString(":")
        }
    }
}
