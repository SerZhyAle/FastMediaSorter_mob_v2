package com.sza.fastmediasorter.core.systeminfo

/**
 * Aggregated System info output.
 *
 * - [maskedText] is the default body shown in the dialog, copied and shared (sensitive values redacted).
 * - [fullText] is the same report with sensitive values revealed; surfaced only on an explicit, confirmed user action.
 * - [hasSensitive] is true when at least one contributor field is marked sensitive; it drives whether the
 *   reveal action is offered. It is always false when no contributor is present (every non-noLegal flavor).
 */
data class SystemInfoReport(
    val maskedText: String,
    val fullText: String,
    val hasSensitive: Boolean,
)
