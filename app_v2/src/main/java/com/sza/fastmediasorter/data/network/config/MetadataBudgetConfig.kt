package com.sza.fastmediasorter.data.network.config

/**
 * Per-file metadata extraction budget (S0237).
 *
 * One short timeout to bound any single metadata read so a misbehaving file cannot
 * stall the rest of the scan. On timeout, callers fall back to listing-only data and
 * mark the corresponding `MediaFile` with
 * [com.sza.fastmediasorter.domain.model.MetadataState.PARTIAL].
 *
 * The default `1500 ms` is the value resolved in strategic spec S0237 §6.2 — chosen
 * so typical EXIF / ID3 reads (~200–400 ms) and video container reads (~400–1000 ms
 * on a healthy NAS) fit comfortably while still cutting off pathological cases.
 */
object MetadataBudgetConfig {

    /**
     * Single-tier per-file timeout used by all metadata extractors that respect this
     * budget. Phase 03 wires it into `SmbMediaScanner`; later phases extend the same
     * value to other transports if measurements warrant.
     */
    const val PER_FILE_TIMEOUT_MS: Long = 1500L

    /**
     * Sentinel that disables the budget. Reserved for unit-test setups that need to
     * exercise the slow path without time pressure. Production code paths must always
     * pass [PER_FILE_TIMEOUT_MS].
     */
    const val DISABLED_VALUE: Long = -1L

    /**
     * Current per-file metadata timeout. Phase 04+ may swap the function body for a
     * configurable provider without touching the call sites in the scanners.
     */
    fun perFileTimeoutMs(): Long = PER_FILE_TIMEOUT_MS
}
