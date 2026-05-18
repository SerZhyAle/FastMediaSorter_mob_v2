package com.sza.fastmediasorter.ui.player.model

/**
 * Types of touch zone hint overlays shown to the user.
 * Each type is tracked independently in DataStore so users see each hint once.
 */
enum class TouchZoneHintType {
    /** Fullscreen mode, images - 9-zone 3×3 grid */
    FULLSCREEN_9ZONE,
    /** Command panel mode - 3 zones (20% left, 60% center, 20% right) */
    COMMAND_PANEL_3ZONE,
    /** Video/audio fullscreen - bottom area reserved for ExoPlayer controls */
    MEDIA_BOTTOM_RESERVED
}
