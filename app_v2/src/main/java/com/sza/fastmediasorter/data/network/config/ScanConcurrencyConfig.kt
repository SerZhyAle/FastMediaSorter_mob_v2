package com.sza.fastmediasorter.data.network.config

/**
 * Tunable concurrency limits for network media scans (S0237).
 *
 * Holds the project-wide defaults referenced by
 * [com.sza.fastmediasorter.data.network.ConnectionThrottleManager] and the upcoming
 * [com.sza.fastmediasorter.data.network.enrichment.MetadataEnrichmentQueue]. Keeping
 * the numbers in a single object means a future change is one constant, not a hunt
 * through several files.
 *
 * The current values are deliberately picked as starting points — see strategic spec
 * S0237 §6.1 for the rationale. Speed-test recommendations stored per resource in
 * `ConnectionThrottleManager.recommendedThreadsCache` still override these defaults
 * at runtime; this object only governs the base.
 */
object ScanConcurrencyConfig {

    /**
     * Default per-resource SMB concurrency. Mirrored in
     * `ConnectionThrottleManager.ProtocolLimits.SMB` (`maxConcurrent`) and
     * `userDefinedNetworkLimit`. Keep all three in sync when the value changes.
     *
     * S0237 §6.1: raised from legacy `2` to `8`.
     */
    const val SMB_DEFAULT_LIMIT: Int = 8

    /**
     * Hard cap. The throttle manager will not allocate more than this many concurrent
     * SMB operations regardless of speed-test recommendations or user overrides — a
     * runaway recommendation should not be able to exhaust the SMBJ client pool.
     */
    const val SMB_MAX_LIMIT: Int = 32

    /**
     * Current SMB concurrency default. Phase 04 swaps the function body to read from a
     * configurable provider (DataStore / SettingsRepository) without touching call sites.
     */
    fun smbLimit(): Int = SMB_DEFAULT_LIMIT
}
