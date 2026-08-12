package com.sza.fastmediasorter.domain.model.devicestatus

/**
 * S1178: what the memory gadget states - free and total RAM, and how long the device has been up.
 *
 * Uptime travels with memory rather than in its own metric because both come from the same read and
 * both answer the same question the gadget exists for: how the device is doing right now.
 */
data class MemoryStatus(
    val availableRamBytes: MetricValue<Long>,
    val totalRamBytes: MetricValue<Long>,
    val uptimeMillis: MetricValue<Long>
)
