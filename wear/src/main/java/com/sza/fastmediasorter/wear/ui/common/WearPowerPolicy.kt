package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * S2536: how strongly the app is currently conserving power.
 *
 * [REDUCED] is the user's own "disable animations" switch. [SAVING] is stronger and is entered
 * automatically at a low charge or under the system power saver, so it also stops motion that
 * carries meaning but costs a continuous full-screen redraw.
 */
enum class PowerPolicyLevel {
    NORMAL,
    REDUCED,
    SAVING
}

/**
 * S2536: what a call site's animation is FOR, declared by the caller.
 *
 * The exemption for meaning-carrying motion used to be keyed to the drawing class, which cannot work
 * here: one composable draws both the audio visualizer and the plain backdrop behind every screen,
 * so a class-keyed rule either froze the visualizer or missed the backdrop.
 */
enum class AnimationIntent {
    /** Pure ornament - the branded backdrop behind the app's screens. */
    DECORATIVE,

    /** Meaningful, but a continuous full-screen redraw - the audio visualizer under a playing track. */
    AMBIENT,

    /** Bounded state feedback - a progress spinner, the swipe-dismiss gesture following the finger. */
    FUNCTIONAL
}

/**
 * S2536: the watch's process-wide answer to "may this animate", mirroring the phone's own copy.
 *
 * Deliberately mirrored rather than shared: the two modules build separately and have no common
 * source set, and this repository already carries the same duplication for the wear settings
 * registry, whose two copies are kept identical by hand. Introducing a shared module to avoid
 * repeating three enums would be a much larger change than the one this ticket is making.
 *
 * The level is snapshot state rather than a plain volatile - unlike the phone, every reader here is
 * a composable, and a plain field would leave a frozen backdrop frozen after the charge recovered
 * because nothing would recompose.
 */
object WearPowerPolicy {

    private var currentLevel by mutableStateOf(PowerPolicyLevel.NORMAL)

    /** Reading this inside a composable subscribes it to later level changes. */
    val level: PowerPolicyLevel
        get() = currentLevel

    /**
     * The policy matrix. [NORMAL] allows everything; [REDUCED] stops ornament only; [SAVING] leaves
     * only bounded state feedback, so a continuous [AnimationIntent.AMBIENT] redraw freezes too.
     */
    fun mayAnimate(intent: AnimationIntent): Boolean = when (currentLevel) {
        PowerPolicyLevel.NORMAL -> true
        PowerPolicyLevel.REDUCED -> intent != AnimationIntent.DECORATIVE
        PowerPolicyLevel.SAVING -> intent == AnimationIntent.FUNCTIONAL
    }

    fun update(level: PowerPolicyLevel) {
        currentLevel = level
    }
}
