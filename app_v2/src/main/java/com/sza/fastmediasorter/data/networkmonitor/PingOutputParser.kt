package com.sza.fastmediasorter.data.networkmonitor

import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeUnavailability

/**
 * S1617: turns what `/system/bin/ping` prints into one outcome of the reachability role.
 *
 * Split from the probe that spawns the process because this half is the only one testable without a
 * device, and strategic §7 puts the expensive mistake here: output that is merely unfamiliar must
 * become "could not measure", never "the host did not answer".
 */
object PingOutputParser {

    private const val FULL_LOSS_PERCENT = 100

    private val ROUND_TRIP = Regex("""time[=<]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""", RegexOption.IGNORE_CASE)
    private val TTL_EXCEEDED = Regex(
        """From\s+([^\s:(]+)[^\r\n]*?(?:time to live exceeded|ttl exceeded)""",
        RegexOption.IGNORE_CASE,
    )
    private val PACKET_LOSS = Regex("""([0-9]+)(?:\.[0-9]+)?%\s*packet loss""", RegexOption.IGNORE_CASE)
    private val NAME_UNRESOLVED = Regex(
        """(unknown host|name or service not known|bad address|no address associated)""",
        RegexOption.IGNORE_CASE,
    )
    private val NETWORK_DOWN = Regex("""network is (unreachable|down)""", RegexOption.IGNORE_CASE)

    fun parse(output: String): HostProbeResult = when {
        output.isBlank() -> unavailable(HostProbeUnavailability.MECHANISM_UNAVAILABLE, "ping printed nothing")
        NAME_UNRESOLVED.containsMatchIn(output) -> unavailable(HostProbeUnavailability.NAME_NOT_RESOLVED, null)
        NETWORK_DOWN.containsMatchIn(output) -> unavailable(HostProbeUnavailability.NO_NETWORK, null)
        else -> readAnswer(output)
    }

    /**
     * Order matters: a hop that answered instead of the target also carries a round-trip time, so
     * the TTL-exceeded line has to be read before the timing line or every hop would read as the
     * destination replying.
     */
    private fun readAnswer(output: String): HostProbeResult {
        val hop = TTL_EXCEEDED.find(output)
        val roundTrip = ROUND_TRIP.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
        val loss = PACKET_LOSS.find(output)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            hop != null -> HostProbeResult.HopAnswered(hop.groupValues[1], roundTrip ?: 0.0)
            roundTrip != null && loss != FULL_LOSS_PERCENT -> HostProbeResult.Reached(roundTrip, null)
            loss == FULL_LOSS_PERCENT -> HostProbeResult.NotReached
            else -> unavailable(HostProbeUnavailability.MECHANISM_UNAVAILABLE, "ping output not recognised")
        }
    }

    private fun unavailable(cause: HostProbeUnavailability, detail: String?): HostProbeResult =
        HostProbeResult.NotMeasurable(cause, detail)
}
