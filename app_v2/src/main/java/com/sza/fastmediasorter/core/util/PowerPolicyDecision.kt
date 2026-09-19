package com.sza.fastmediasorter.core.util

/**
 * S3276: the reason calculated by the power policy resolver.
 *
 * Computed once in the power policy layer and is the only source the settings screen may read.
 */
enum class PowerPolicyReason {
    NONE,
    USER_ALWAYS,
    SYSTEM_SAVER,
    LOW_BATTERY,
    ANIMATION_SWITCH
}

/**
 * S3276: holds the active [PowerPolicyLevel] and the [PowerPolicyReason] that decided it.
 */
data class PowerPolicyDecision(
    val level: PowerPolicyLevel,
    val reason: PowerPolicyReason
)
