package com.sza.fastmediasorter.core.util

/**
 * S2250: the single process-wide answer to "may this animate", fed by one subscription to the
 * settings flow in the application class.
 *
 * Animation sites live in custom views, adapters and helper managers with no ViewModel and no
 * repository access, and they ask from a draw or click path - so the answer has to be readable
 * synchronously, without injection, disk or a coroutine.
 *
 * The default is "allowed": a cold start reads this before the first settings emission arrives, and
 * defaulting the other way would show a frozen first frame to a user who never asked for one.
 *
 * Meaning-carrying animations do not consult this - the audio visualizer, the camera focus ring and
 * the filename overlay's auto-hide keep running with the setting on, because a frozen visualizer is
 * indistinguishable from stopped playback (S2250 ADR-2).
 */
object AnimationPolicy {

    @Volatile
    private var animationsDisabled: Boolean = false

    /** True when transitions and decorative animators may run. */
    val isAnimationAllowed: Boolean
        get() = !animationsDisabled

    fun update(disabled: Boolean) {
        animationsDisabled = disabled
    }
}
