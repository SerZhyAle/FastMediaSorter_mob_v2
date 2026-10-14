package com.sza.fastmediasorter.domain.model

import java.net.Inet6Address
import java.net.InetAddress

/** What a failed connection was aimed at: the exchange server, or another device's own sharing service. */
enum class ExchangeCheckTarget { SERVER, DEVICE }

/** Whether the target sits on the person's own network or is reached from outside it. */
enum class ExchangeCheckRoute { NETWORK, INTERNET }

enum class ExchangeAddressFamily { IPV4, IPV6 }

/**
 * What the phone could read about itself without a request: null is "not tested". A step the evidence names as
 * failed is put first; a step it clears is left out of the walk.
 */
data class ExchangeCheckEvidence(
    val vpnActive: Boolean? = null,
    val deviceHasIpv4: Boolean? = null,
    val deviceHasIpv6: Boolean? = null,
    val certificateTimeInvalid: Boolean = false,
)

/**
 * The walk of the check ladder of contract DEVICE-EXCHANGE 25 item CY for one failure, and the two facts about a
 * host that decide which steps apply to it.
 */
object ExchangeCheckLadder {

    private const val BYTE_MASK = 0xFF
    private const val CGNAT_FIRST_BYTE = 100
    private const val CGNAT_SECOND_MASK = 0xC0
    private const val CGNAT_SECOND_VALUE = 0x40
    private const val ULA_MASK = 0xFE
    private const val ULA_VALUE = 0xFC
    private const val IPV4_OCTETS = 4

    private val IPV4_LITERAL = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val IPV6_LITERAL = Regex("""[0-9a-f:.]+""")
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".internal")

    /**
     * The steps to offer, in order. A failure with no step carries its own advice and walks nothing; a failure
     * with a reason of its own on the wire offers its one step; the three without one (item CZ) are walked from
     * the row's step to the end, with the steps the [evidence] names as failed first and those it clears left
     * out. A step that does not apply to [route] and [target] is skipped, never shown as passed.
     */
    fun walk(
        message: ExchangeMessage,
        target: ExchangeCheckTarget,
        route: ExchangeCheckRoute,
        evidence: ExchangeCheckEvidence,
        family: ExchangeAddressFamily?,
    ): List<ExchangeCheckStep> {
        val start = message.step ?: return emptyList()
        val candidates = if (message.walked) {
            ExchangeCheckStep.entries.filter { it.ordinal >= start.ordinal }
        } else {
            listOf(start)
        }
        val applicable = candidates.filter { applies(it, target, route) }
        return if (message.walked) reorder(applicable, target, route, evidence, family) else applicable
    }

    private fun reorder(
        applicable: List<ExchangeCheckStep>,
        target: ExchangeCheckTarget,
        route: ExchangeCheckRoute,
        evidence: ExchangeCheckEvidence,
        family: ExchangeAddressFamily?,
    ): List<ExchangeCheckStep> {
        val failed = buildList {
            if (evidence.vpnActive == true) add(ExchangeCheckStep.VPN)
            if (familyMissing(evidence, family)) add(ExchangeCheckStep.IP_FAMILY)
            if (evidence.certificateTimeInvalid) add(ExchangeCheckStep.DEVICE_CLOCK)
        }.filter { applies(it, target, route) }
        val cleared = buildList {
            if (evidence.vpnActive == false) add(ExchangeCheckStep.VPN)
            if (familyPresent(evidence, family)) add(ExchangeCheckStep.IP_FAMILY)
        }
        return failed + applicable.filter { it !in failed && it !in cleared }
    }

    private fun applies(step: ExchangeCheckStep, target: ExchangeCheckTarget, route: ExchangeCheckRoute): Boolean {
        val routeFits = when (step.applies) {
            ExchangeCheckStep.Applies.ANY -> true
            ExchangeCheckStep.Applies.NETWORK -> route == ExchangeCheckRoute.NETWORK
            ExchangeCheckStep.Applies.INTERNET -> route == ExchangeCheckRoute.INTERNET
        }
        return routeFits && (!step.windowsOnly || target == ExchangeCheckTarget.SERVER)
    }

    private fun familyMissing(evidence: ExchangeCheckEvidence, family: ExchangeAddressFamily?) = when (family) {
        ExchangeAddressFamily.IPV4 -> evidence.deviceHasIpv4 == false
        ExchangeAddressFamily.IPV6 -> evidence.deviceHasIpv6 == false
        null -> false
    }

    private fun familyPresent(evidence: ExchangeCheckEvidence, family: ExchangeAddressFamily?) = when (family) {
        ExchangeAddressFamily.IPV4 -> evidence.deviceHasIpv4 == true
        ExchangeAddressFamily.IPV6 -> evidence.deviceHasIpv6 == true
        null -> false
    }

    /**
     * Own network for a private, link-local, loopback, carrier-grade-NAT or unique-local literal and for a name
     * with a local suffix or no dot; reached from outside for every other host.
     */
    fun routeOf(host: String): ExchangeCheckRoute {
        val bare = bare(host)
        val onNetwork = literalAddress(bare)?.let(::isOwnNetworkAddress)
            ?: (LOCAL_SUFFIXES.any { bare.endsWith(it) } || '.' !in bare)
        return if (onNetwork) ExchangeCheckRoute.NETWORK else ExchangeCheckRoute.INTERNET
    }

    /** The family of a literal address, or null for a name: a name's family is the resolver's to say. */
    fun familyOf(host: String): ExchangeAddressFamily? = when (literalAddress(bare(host))) {
        null -> null
        is Inet6Address -> ExchangeAddressFamily.IPV6
        else -> ExchangeAddressFamily.IPV4
    }

    private fun bare(host: String): String =
        host.trim().removePrefix("[").removeSuffix("]").substringBefore('%').lowercase()

    // Only a literal reaches getByName, so the lookup never goes to the resolver.
    private fun literalAddress(bare: String): InetAddress? {
        val literal = when {
            ':' in bare -> IPV6_LITERAL.matches(bare)
            else -> IPV4_LITERAL.matches(bare) && bare.split('.').all { it.toInt() <= BYTE_MASK }
        }
        return if (literal) runCatching { InetAddress.getByName(bare) }.getOrNull() else null
    }

    private fun isOwnNetworkAddress(address: InetAddress): Boolean {
        val bytes = address.address
        val first = bytes[0].toInt() and BYTE_MASK
        return when {
            address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress -> true
            bytes.size == IPV4_OCTETS ->
                first == CGNAT_FIRST_BYTE && (bytes[1].toInt() and CGNAT_SECOND_MASK) == CGNAT_SECOND_VALUE
            else -> (first and ULA_MASK) == ULA_VALUE
        }
    }
}
