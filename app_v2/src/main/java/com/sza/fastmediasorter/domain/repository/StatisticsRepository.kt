package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.stats.StatsSnapshot

/**
 * Read/maintenance contract for local usage statistics (S0473).
 *
 * The write path (event recording) is the separate `StatsSink`; this repository is the
 * read side plus the two lifecycle operations: the always-on launch record and the
 * detailed-data wipe performed when the user turns collection off.
 */
interface StatisticsRepository {

    /** All-time snapshot: always-on baseline plus detailed opt-in counters. */
    suspend fun getSnapshot(): StatsSnapshot

    /** Erases detailed activity; always-on baseline survives (strategic §3.2 / ADR-2). */
    suspend fun wipeDetailed()

    /** Always-on per-launch record, independent of the opt-in toggle. */
    suspend fun recordLaunch(installVersion: String, installFlavor: String)
}
