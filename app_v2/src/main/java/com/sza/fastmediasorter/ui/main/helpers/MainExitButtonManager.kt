package com.sza.fastmediasorter.ui.main.helpers

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import com.sza.fastmediasorter.widget.QuickAudioRecorderService
import com.sza.fastmediasorter.worker.ScheduledOperationsWorker
import timber.log.Timber

/**
 * S0759 - drives the top-left exit button so it minimizes (instead of closing) the app whenever a
 * background function is live, and only fully closes when nothing is running in the background.
 *
 * Mode is recomputed from REAL activity each time [refresh] is called (window resume / before the
 * button is shown), never cached at startup:
 *  - MINIMIZE branch: any foreground service is alive (background music, mic recording, scheduled file
 *    operations) OR the left-edge gesture overlay is enabled. Action = [onMinimize] (moveTaskToBack).
 *    MUST NOT stop any service - that is the whole point of the ticket.
 *  - CLOSE branch: no background function active. Action = [onFullExit] (current teardown: stop
 *    services first to avoid OS double-startup, then finishAffinity).
 *
 * Long-press = full exit regardless of background activity: a short toast tells the user the
 * background functions were stopped, then after a brief delay the teardown runs.
 *
 * The Activity owns the button and the teardown; this manager only decides the mode and routes taps.
 */
class MainExitButtonManager(
    private val exitButton: MaterialButton,
    private val onMinimize: () -> Unit,
    private val onFullExit: () -> Unit,
) {

    /** Cached left-edge gesture overlay setting; updated by the settings collector via [setGestureOverlayEnabled]. */
    private var gestureOverlayEnabled: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    fun setupClickHandlers() {
        exitButton.setOnClickListenerDebounced {
            handleClick()
        }
        exitButton.setOnLongClickListener {
            handleLongPress()
            true
        }
        refresh()
    }

    /** Re-read the edge-gesture setting from the settings stream and refresh the button mode. */
    fun setGestureOverlayEnabled(enabled: Boolean) {
        if (gestureOverlayEnabled != enabled) {
            gestureOverlayEnabled = enabled
        }
        refresh()
    }

    /** Recompute minimize-vs-close from live activity and update the icon + content description. */
    fun refresh() {
        applyIconForMode(isBackgroundActive())
    }

    private fun handleClick() {
        val minimize = isBackgroundActive()
        Timber.d(
            "S0759: exit tap minimize=$minimize music=${AudioPlaybackService.isRunning} " +
                "rec=${QuickAudioRecorderService.isRecording} sched=${ScheduledOperationsWorker.isRunning} " +
                "edge=$gestureOverlayEnabled",
        )
        if (minimize) {
            // Minimize keeps every foreground service alive (the core S0759 contract).
            onMinimize()
        } else {
            onFullExit()
        }
    }

    private fun handleLongPress() {
        // Long-press is an explicit "kill everything" gesture. Show the toast first so the user sees the
        // confirmation that background functions are being stopped, then run the full teardown after a
        // short delay (owner decision Q4: ~2 s).
        Toast.makeText(
            exitButton.context,
            R.string.exit_background_stopped_toast,
            Toast.LENGTH_SHORT,
        ).show()
        mainHandler.postDelayed({ onFullExit() }, LONG_PRESS_EXIT_DELAY_MS)
    }

    /**
     * True when at least one relevant background function is active right now. Each signal is the
     * cheapest reliable primitive for its trigger; if a future trigger has no live signal, log
     * Timber.i and treat it as inactive rather than guessing.
     */
    private fun isBackgroundActive(): Boolean {
        return AudioPlaybackService.isRunning ||
            QuickAudioRecorderService.isRecording ||
            ScheduledOperationsWorker.isRunning ||
            gestureOverlayEnabled
    }

    private fun applyIconForMode(minimize: Boolean) {
        if (minimize) {
            exitButton.setIconResource(R.drawable.ic_double_arrow_down)
            exitButton.contentDescription = exitButton.context.getString(R.string.minimize_to_background)
        } else {
            exitButton.setIconResource(R.drawable.ic_cancel)
            exitButton.contentDescription = exitButton.context.getString(R.string.exit)
        }
    }

    companion object {
        /** Owner decision Q4: hold the "background functions stopped" toast on screen before teardown. */
        private const val LONG_PRESS_EXIT_DELAY_MS = 2_000L
    }
}
