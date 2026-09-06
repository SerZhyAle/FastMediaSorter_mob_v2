package com.sza.fastmediasorter.domain.model.network

/**
 * Three-valued Wi-Fi hotspot state.
 *
 * Note: [UNKNOWN] is a real, renderable outcome and not an error.
 * No consumer may collapse [UNKNOWN] to [DISABLED].
 */
enum class HotspotState {
    ENABLED,
    DISABLED,
    UNKNOWN
}
