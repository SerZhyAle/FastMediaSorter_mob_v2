package com.sza.fastmediasorter.ui.player.helpers

/**
 * Holds a requested player finish until every error presentation in flight has ended.
 *
 * The loader emits `ShowError` and `FinishActivity` back to back. The error surface needs the
 * settings first, so an immediate finish always won the race and the user saw the player vanish
 * with no reason given. Every presentation is a ticket that completes exactly once - shown and
 * dismissed, or skipped - and the finish runs at most once, after the last ticket completes.
 *
 * Main-thread only: every caller is the player's event handler or a dialog/snackbar callback.
 */
class PlayerErrorFinishSequencer(private val finish: () -> Unit) {

    private var presentationsInFlight = 0
    private var finishRequested = false
    private var finished = false

    /** Returns the completion of this presentation; calling it again is a no-op. */
    fun beginPresentation(): () -> Unit {
        presentationsInFlight++
        var completed = false
        return {
            if (!completed) {
                completed = true
                presentationsInFlight--
                finishIfReady()
            }
        }
    }

    fun requestFinish() {
        finishRequested = true
        finishIfReady()
    }

    private fun finishIfReady() {
        if (finishRequested && presentationsInFlight == 0 && !finished) {
            finished = true
            finish()
        }
    }
}
