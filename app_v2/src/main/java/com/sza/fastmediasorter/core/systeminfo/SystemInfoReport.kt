package com.sza.fastmediasorter.core.systeminfo

/**
 * Aggregated System info output.
 *
 * [sections] keeps the gathered structure available to the standalone report screen, while [fullText]
 * remains the portable representation used by copy, save and share actions.
 */
data class SystemInfoReport(
    val sections: List<SystemInfoSection>,
    val maskedText: String,
    val fullText: String,
    val hasSensitive: Boolean,
)
