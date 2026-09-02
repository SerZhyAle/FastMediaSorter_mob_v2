package com.sza.fastmediasorter.domain.networkmonitor

/**
 * S1617: why a reachability question could not be answered at all.
 *
 * This is not a failure of the host - it is a failure of the measurement, and the two must stay
 * apart. Strategic §7 names a live host reported as dead as the first risk of this feature, and
 * that mistake is made the moment "the probe could not run" is folded into "the host is down".
 */
enum class HostProbeUnavailability {

    /** No usable network on the device, so nothing could have answered. */
    NO_NETWORK,

    /** The host is a name and no address was resolved for it. */
    NAME_NOT_RESOLVED,

    /** The mechanism itself did not run - binary absent, policy refusal, or no answer in time. */
    MECHANISM_UNAVAILABLE,
}

/** S1617: the answer to one reachability question. Never a boolean - see [HostProbeUnavailability]. */
sealed interface HostProbeResult {

    /** The target itself answered. [respondingAddress] is what answered, when the probe knows it. */
    data class Reached(
        val roundTripMillis: Double,
        val respondingAddress: String?,
    ) : HostProbeResult

    /**
     * An intermediate node answered instead of the target, because the probe's TTL ran out there.
     * This is what makes a hop-by-hop path possible; it is neither reached nor unreachable.
     */
    data class HopAnswered(
        val hopAddress: String,
        val roundTripMillis: Double,
    ) : HostProbeResult

    /** The measurement ran and nothing answered within the budget. */
    data object NotReached : HostProbeResult

    /** The measurement did not run, or its answer cannot be trusted. [detail] is for the log, not the user. */
    data class NotMeasurable(
        val cause: HostProbeUnavailability,
        val detail: String? = null,
    ) : HostProbeResult
}

/**
 * S1617: the one way the monitor asks whether a host answers.
 *
 * Measured on the owner's phone 2026-08-18 (strategic §6.1): spawning the system `ping` works from
 * the app's own SELinux domain and yields both a real round trip and the address of an intermediate
 * hop, while a raw-socket ladder is impossible on Android and the platform's own reachability call
 * degrades to a TCP probe of a port almost nothing listens on. The role exists so the mechanism can
 * differ per firmware without any caller learning which one answered.
 */
interface HostProbe {

    /**
     * @param timeoutMillis budget for the whole attempt, including process start.
     * @param ttl when set, ask for an answer from the node that many hops away rather than from the
     *   target. A mechanism that cannot address a single hop must answer
     *   [HostProbeResult.NotMeasurable] rather than silently probing the target instead.
     */
    suspend fun probe(host: String, timeoutMillis: Long, ttl: Int? = null): HostProbeResult
}
