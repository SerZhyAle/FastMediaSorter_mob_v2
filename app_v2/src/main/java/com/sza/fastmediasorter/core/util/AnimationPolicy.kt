package com.sza.fastmediasorter.core.util

import java.util.concurrent.CopyOnWriteArraySet

/**
 * S2250 / S2536: the single process-wide answer to "may this animate", fed by one subscription to
 * the power state observer in the application class.
 *
 * Animation sites live in custom views, adapters and helper managers with no ViewModel and no
 * repository access, and they ask from a draw or click path - so the answer has to be readable
 * synchronously, without injection, disk or a coroutine.
 *
 * The default is "allowed": a cold start reads this before the first settings emission arrives, and
 * defaulting the other way would show a frozen first frame to a user who never asked for one.
 *
 * S2536 replaced the boolean with a level, because the user's cosmetic switch and an automatic
 * low-battery mode are not the same strength. Meaning-carrying motion survives the switch and not
 * the saving mode: a frozen visualizer is indistinguishable from stopped playback, which is why
 * S2250 exempted it, but at ten percent charge a full-screen redraw measured at about 1.5 cores on
 * the watch is not worth that clarity. Which of the two a site is, is now declared by the site
 * through [AnimationIntent] rather than inferred from its class - one class serves both roles.
 */
object AnimationPolicy {

    @Volatile
    private var currentLevel: PowerPolicyLevel = PowerPolicyLevel.NORMAL

    /** The level in force right now. */
    val level: PowerPolicyLevel
        get() = currentLevel

    /**
     * True when transitions and decorative animators may run.
     *
     * Kept as the decorative question so the call sites S2250 already converted read unchanged.
     */
    val isAnimationAllowed: Boolean
        get() = mayAnimate(AnimationIntent.DECORATIVE)

    /**
     * The policy matrix. [PowerPolicyLevel.NORMAL] allows everything; [PowerPolicyLevel.REDUCED]
     * stops ornament only; [PowerPolicyLevel.SAVING] leaves only bounded state feedback, so a
     * continuous [AnimationIntent.AMBIENT] redraw freezes to a static frame too.
     */
    fun mayAnimate(intent: AnimationIntent): Boolean = when (currentLevel) {
        PowerPolicyLevel.NORMAL -> true
        PowerPolicyLevel.REDUCED -> intent != AnimationIntent.DECORATIVE
        PowerPolicyLevel.SAVING -> intent == AnimationIntent.FUNCTIONAL
    }

    /**
     * S2536: fired after the level actually changed, so a site already holding a frozen frame can ask
     * again and resume.
     *
     * The synchronous read above is what draw paths use and it stays the primary interface; this is
     * only for the sites that must ACT on a change rather than answer one - a paused animator has no
     * draw pass left in which to notice. Listeners run on whichever thread produced the change, which
     * is the settings collector's, so a listener touching views hops to the main thread itself.
     */
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun addLevelListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeLevelListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun update(level: PowerPolicyLevel) {
        if (currentLevel == level) return
        currentLevel = level
        listeners.forEach { it() }
    }
}
