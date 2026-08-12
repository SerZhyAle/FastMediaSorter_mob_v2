package com.sza.fastmediasorter.domain.model.sensors

/**
 * S1179: the hardware question a sensor gadget asks before it offers itself on the launcher desktop.
 *
 * Hardware only. Whether the matching runtime permission was granted is a deliberately separate
 * axis: folding the two together would drop a gadget from the picker when the user refuses a
 * permission, instead of leaving the tile in place in an empty state (strategic ADR-3, §11.5).
 */
enum class SensorCapability {
    COMPASS,
    LOCATION,
    STEP_COUNTER,
}
