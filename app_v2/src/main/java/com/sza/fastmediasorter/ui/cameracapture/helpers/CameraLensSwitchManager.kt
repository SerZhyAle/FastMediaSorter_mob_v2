package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.view.View
import android.widget.TextView
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * S1987: owns what the lens-switch control shows while the session rebinds to another physical
 * camera, and refuses a second request made inside that window.
 *
 * The rebind opens a camera through CameraX, which must happen on the main thread and holds it for
 * as long as the optics take to open, so the screen cannot repaint while it runs. Before this
 * manager the button stayed enabled and silent for that whole window: presses made inside it were
 * not dropped but queued in the input channel, and cycled several lenses at once the moment the
 * thread freed, while nothing on screen ever said a switch was under way.
 *
 * Both entry points route here - the button and the swipe gesture - so the refusal covers the pair
 * rather than only the control it was reported on. A standalone helper rather than Activity code,
 * per CLAUDE.md Rule 3, and because that Activity sits on detekt's `TooManyFunctions` ceiling.
 */
class CameraLensSwitchManager(
    private val switchButton: View,
    private val lensLabel: TextView,
    private val onSwitch: () -> Unit,
    private val onRestoreLabel: () -> Unit,
) {

    private var inFlight = false

    /** Handles a switch request; one arriving while the previous rebind still runs is refused. */
    fun onRequested() {
        Timber.d("S1987: lens switch requested, inFlight=$inFlight")
        if (inFlight) return
        inFlight = true
        switchButton.isEnabled = false
        lensLabel.setText(R.string.camera_lens_switching)
        // Posted so this frame paints the busy state before the blocking rebind begins. Run inline,
        // the rebind would finish before the disabled button ever reached the screen.
        switchButton.post {
            try {
                onSwitch()
            } finally {
                inFlight = false
                switchButton.isEnabled = true
                // A rebind that changed nothing fires no capabilities callback, so the label would
                // stay on "switching" for good; restoring it here covers that path too.
                onRestoreLabel()
            }
        }
    }
}
