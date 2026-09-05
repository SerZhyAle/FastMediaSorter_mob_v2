package com.sza.fastmediasorter.core.util

/**
 * S2536: what a call site's animation is FOR, declared by the caller.
 *
 * S2250 exempted meaning-carrying motion by naming the drawing CLASS. That cannot hold: the
 * wave/particle view is both the audio visualizer and the launcher desktop's plain backdrop, so a
 * class-keyed exemption either froze the visualizer or missed the wallpaper - and it missed the
 * wallpaper, which ran behind the icons for as long as the desktop was foreground.
 */
enum class AnimationIntent {
    /** Pure ornament - screen transitions, crossfades, the launcher desktop backdrop. */
    DECORATIVE,

    /** Meaningful, but a continuous full-screen redraw - the audio visualizers. */
    AMBIENT,

    /** Bounded state feedback - the camera focus ring, the filename overlay's auto-hide. */
    FUNCTIONAL
}
