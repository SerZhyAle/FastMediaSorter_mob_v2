package com.sza.fastmediasorter.core.memory

/**
 * Records current memory state at a named checkpoint.
 *
 * Implementation captures Java heap used/max, native heap allocated/reserved/free
 * at call-time. Non-blocking. Safe to call from main thread.
 *
 * The single canonical event format (see `MemoryProbeImpl`) is the only memory
 * observability channel introduced by S0207 — replaces the ad-hoc `MEMORY_DEBUG`
 * lines that existed before Phase 01.
 */
interface MemoryProbe {

    /**
     * Capture and record memory state at [checkpoint].
     *
     * @param checkpoint Lifecycle anchor — see [MemoryCheckpoint].
     * @param scenarioTag Optional free-form tag identifying the active user scenario
     *  (e.g. `"audio"`, `"video"`, `"browse"`). `null` is logged as `"NONE"`.
     */
    fun record(checkpoint: MemoryCheckpoint, scenarioTag: String? = null)
}
