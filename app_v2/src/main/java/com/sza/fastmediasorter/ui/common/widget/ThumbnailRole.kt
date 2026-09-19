package com.sza.fastmediasorter.ui.common.widget

/**
 * Where a thumbnail is drawn in the UI. Pins the override size, disk-cache strategy and placeholder
 * convention for [MediaItemThumbnailBinder.bind] so a call site names its role instead of assembling
 * its own Glide options (`docs/ui/PHONE_UI_COMPONENT_PATTERNS.md` section 2.2).
 */
enum class ThumbnailRole {
    /** A row in a vertical list - compact, cached aggressively, cropped to a small square. */
    LIST_ROW,

    /** A cell in a grid - same footprint as [LIST_ROW] today, named separately for layout intent. */
    GRID_CELL,

    /** A full-size preview surface - no downscale override, resource-only disk cache. */
    PREVIEW
}
