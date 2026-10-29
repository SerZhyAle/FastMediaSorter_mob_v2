package com.sza.fastmediasorter.ui.player.helpers

/**
 * Decides which decorative-video attempt still owns the audio background and whether a lifecycle
 * event may rebuild it after a failure. Kept free of MediaPlayer so the rules are unit-testable.
 *
 * A token is handed out per attempt; any callback carrying an older token belongs to a player that
 * was already superseded or released and must not touch the current views or flags.
 */
class AudioVisualizationRecoveryPolicy(
    private val maxLifecycleRecoveries: Int = DEFAULT_MAX_LIFECYCLE_RECOVERIES,
) {

    private var generation = 0
    private var consecutiveFailures = 0

    fun begin(): Int {
        generation++
        return generation
    }

    fun invalidate() {
        generation++
    }

    fun isCurrent(token: Int): Boolean = token == generation

    /** Returns false for a stale attempt, whose prepare must be ignored. */
    fun onPrepared(token: Int): Boolean {
        if (!isCurrent(token)) return false
        consecutiveFailures = 0
        return true
    }

    /** Returns false for a stale attempt, whose error must not replace the current background. */
    fun onFailure(token: Int): Boolean {
        if (!isCurrent(token)) return false
        consecutiveFailures++
        generation++
        return true
    }

    fun onStartFailure() {
        consecutiveFailures++
        generation++
    }

    /**
     * A media server that keeps dying would otherwise be re-hit on every isPlaying transition,
     * which buffering toggles without any user action.
     */
    fun mayRecoverFromLifecycle(): Boolean = consecutiveFailures < maxLifecycleRecoveries

    fun resetFailures() {
        consecutiveFailures = 0
    }

    companion object {
        const val DEFAULT_MAX_LIFECYCLE_RECOVERIES = 3
    }
}
