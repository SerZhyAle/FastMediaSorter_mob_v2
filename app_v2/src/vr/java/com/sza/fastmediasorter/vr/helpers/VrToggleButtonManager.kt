package com.sza.fastmediasorter.vr.helpers

import android.widget.ImageButton
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * Manages the immersive toggle button state in the player command bar (VR flavor only).
 *
 * The button shows either "Immersive view" (panel mode → launch immersive)
 * or "Exit immersive" (immersive mode → return to panel), switching icon and
 * content description accordingly.
 *
 * Logic coupling: VrPlayerActivity calls [updateState] after a routing decision
 * is made. The actual route-switch is performed by [onToggleRequested] which
 * invokes a caller-supplied lambda so this class stays UI-only.
 */
internal class VrToggleButtonManager(
    private val button: ImageButton,
    private val onSwitchToPanelRequested: () -> Unit,
    private val onSwitchToImmersiveRequested: () -> Unit,
) {
    private var isImmersiveMode: Boolean = false

    /** Call this after XR session starts (immersive) or when returning to panel mode. */
    fun updateState(immersive: Boolean) {
        isImmersiveMode = immersive
        if (immersive) {
            button.setImageResource(R.drawable.ic_vr_exit)
            button.contentDescription = button.context.getString(R.string.vr_toggle_exit_description)
        } else {
            button.setImageResource(R.drawable.ic_vr_3d)
            button.contentDescription = button.context.getString(R.string.vr_toggle_enter_description)
        }
        Timber.d("VrToggleButtonManager: state updated — immersive=$immersive")
    }

    /**
     * Called when the user taps the 3DVR button or presses the left thumbstick.
     * Delegates to the appropriate lambda based on current mode.
     */
    fun onToggleRequested() {
        Timber.i("VrToggleButtonManager: toggle requested (isImmersive=$isImmersiveMode)")
        if (isImmersiveMode) {
            onSwitchToPanelRequested()
        } else {
            onSwitchToImmersiveRequested()
        }
    }
}
