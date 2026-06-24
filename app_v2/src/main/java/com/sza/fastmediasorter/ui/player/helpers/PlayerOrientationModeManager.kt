package com.sza.fastmediasorter.ui.player.helpers

/**
 * Single decision point that maps device orientation to a player display mode (S0667 ADR-1).
 *
 * Shared by both player hosts so they behave identically: landscape implies fullscreen,
 * portrait implies the command panel. Pure and stateless - hosts supply their own gate inputs.
 */
class PlayerOrientationModeManager {

    /**
     * Resolve the display mode the player should adopt for the current orientation.
     *
     * @param followsDevice whether rotation currently follows the device (OS auto-rotate or app
     *   sensor); false means orientation is locked/forced and no auto switch must happen (§6.1).
     * @param isVisualMedia whether the active media is visual (video or image); audio/text/document
     *   are out of scope.
     * @return the target mode, or null when no auto switch should be applied.
     */
    fun resolve(
        isLandscape: Boolean,
        followsDevice: Boolean,
        isVisualMedia: Boolean
    ): PlayerDisplayMode? {
        if (!followsDevice || !isVisualMedia) return null
        // Rotation always dictates the mode - last word goes to orientation (§6.2).
        return if (isLandscape) PlayerDisplayMode.FULLSCREEN else PlayerDisplayMode.COMMAND_PANEL
    }
}

enum class PlayerDisplayMode {
    FULLSCREEN,
    COMMAND_PANEL
}
