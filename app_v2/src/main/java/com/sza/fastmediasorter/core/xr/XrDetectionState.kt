package com.sza.fastmediasorter.core.xr

/**
 * Combined VR availability state - device detection × user master toggle preference.
 * Consumers (Settings UI, future Stage 1 entry buttons) read this single value.
 */
enum class XrDetectionState {
    /** Device has no XR runtime or runtime not reachable. VR features must hide. */
    NONE,

    /** Device is XR-capable but the user disabled the master toggle. VR features hide. */
    AVAILABLE_DISABLED_BY_USER,

    /** Device is XR-capable and the user enabled the master toggle. VR features may show. */
    AVAILABLE_ENABLED,
}
