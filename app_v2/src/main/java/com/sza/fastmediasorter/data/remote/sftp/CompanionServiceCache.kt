package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.domain.model.HostPort

/**
 * LAN-DISCOVERY rule 5: discovered companions are cached with a TTL, and an entry is probed once
 * (one mDNS re-resolve) before it is invalidated. An expired entry stays served while its probe runs,
 * so a companion that is still present never drops out of the candidate list between announcements.
 *
 * Pure Kotlin and clock-agnostic so the rules are unit-testable without `NsdManager`; the caller owns
 * the probe itself and reports its outcome through [put] (success) or [probeFailed].
 */
class CompanionServiceCache(private val ttlMs: Long = DEFAULT_TTL_MS) {

    /** A cached endpoint, and the service name to re-resolve when this lookup started a probe. */
    data class Lookup(val endpoint: HostPort, val probeServiceName: String?)

    private data class Entry(
        val endpoint: HostPort,
        val serviceName: String,
        val expiresAtMs: Long,
        val probePending: Boolean
    )

    // canonical fingerprint -> entry of the announcing companion.
    private val entries = HashMap<String, Entry>()

    /** Records a resolved service; also the success outcome of a probe, which clears its pending flag. */
    @Synchronized
    fun put(fingerprint: String, endpoint: HostPort, serviceName: String, nowMs: Long) {
        entries[fingerprint] = Entry(endpoint, serviceName, nowMs + ttlMs, probePending = false)
    }

    /** The cached endpoint for [fingerprint]; an expired entry with no probe yet starts exactly one. */
    @Synchronized
    fun lookup(fingerprint: String, nowMs: Long): Lookup? {
        val entry = entries[fingerprint] ?: return null
        val startProbe = nowMs >= entry.expiresAtMs && !entry.probePending
        if (startProbe) entries[fingerprint] = entry.copy(probePending = true)
        return Lookup(entry.endpoint, entry.serviceName.takeIf { startProbe })
    }

    /** Marks the entry of [fingerprint] for a probe; returns the service name to probe, or null if none. */
    @Synchronized
    fun requestProbeForFingerprint(fingerprint: String): String? {
        val entry = entries[fingerprint]?.takeUnless { it.probePending } ?: return null
        entries[fingerprint] = entry.copy(probePending = true)
        return entry.serviceName
    }

    /** Marks every entry announced as [serviceName] for a probe; true when a probe must be started. */
    @Synchronized
    fun requestProbeForService(serviceName: String): Boolean {
        val toProbe = entries.filterValues { it.serviceName == serviceName && !it.probePending }
        toProbe.forEach { (fingerprint, entry) -> entries[fingerprint] = entry.copy(probePending = true) }
        return toProbe.isNotEmpty()
    }

    /** The probe of [serviceName] failed: drops its pending entries. Entries not under probe stay. */
    @Synchronized
    fun probeFailed(serviceName: String) {
        val failed = entries.filterValues { it.serviceName == serviceName && it.probePending }.keys
        failed.forEach { entries.remove(it) }
    }

    @Synchronized
    fun clear() = entries.clear()

    companion object {
        /** The DNS-SD record TTL default the contract names; `NsdManager` exposes no per-record TTL. */
        const val DEFAULT_TTL_MS = 120_000L
    }
}
