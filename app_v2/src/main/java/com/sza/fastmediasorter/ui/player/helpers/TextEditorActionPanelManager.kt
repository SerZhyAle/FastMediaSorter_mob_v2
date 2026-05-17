package com.sza.fastmediasorter.ui.player.helpers

import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.sza.fastmediasorter.util.GoogleKeepAvailabilityChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0189: manages the 5-action editor buttons (Save, Save & Close, Save & Send, Send to Keep,
 * Cancel) and the dirty-state background tint on the [panel] container.
 *
 * The buttons now live inside the top [editorToolbar] (moved up from the former bottom action
 * panel so the soft keyboard does not cover them). [panel] is the LinearLayout that hosts them.
 *
 * Extracted from [TextViewerManager] to keep TextViewerManager under the 1500-LOC limit.
 */
class TextEditorActionPanelManager(
    private val panel: LinearLayout,
    private val btnSave: ImageButton,
    private val btnSaveClose: ImageButton,
    private val btnSaveSend: ImageButton,
    private val btnSendKeep: ImageButton,
    private val btnCancel: ImageButton,
    private val keepChecker: GoogleKeepAvailabilityChecker,
    private val dirtyTracker: TextEditorDirtyStateTracker,
    private val coroutineScope: CoroutineScope
) {
    /**
     * Callback interface for the 5-action panel.
     * Each callback is invoked on the UI thread.
     */
    interface ActionPanelCallbacks {
        fun onSave()
        fun onSaveAndClose()
        fun onSaveAndSend()
        fun onSendToKeep()
        fun onCancel()
    }

    private val dirtyBackground = android.graphics.Color.parseColor("#992C2C")  // warm red — clearly different from default panel
    // S0189: remember the panel's original background so we can restore it on clean state
    // (toolbar background is a colour resource — TRANSPARENT would lose it).
    private val originalBackground = panel.background

    /**
     * Wire click listeners and query Keep availability.
     * Call once after [TextViewerManager] is initialised.
     */
    fun setup(callbacks: ActionPanelCallbacks) {
        btnSave.setOnClickListener {
            Timber.d("S0189: TextEditorActionPanelManager btnSave clicked")
            callbacks.onSave()
        }
        btnSaveClose.setOnClickListener {
            Timber.d("S0189: TextEditorActionPanelManager btnSaveClose clicked")
            callbacks.onSaveAndClose()
        }
        btnSaveSend.setOnClickListener {
            Timber.d("S0189: TextEditorActionPanelManager btnSaveSend clicked")
            callbacks.onSaveAndSend()
        }
        btnSendKeep.setOnClickListener {
            Timber.d("S0189: TextEditorActionPanelManager btnSendKeep clicked")
            callbacks.onSendToKeep()
        }
        btnCancel.setOnClickListener {
            Timber.d("S0189: TextEditorActionPanelManager btnCancel clicked")
            callbacks.onCancel()
        }

        // Show Send-to-Keep only when Keep is available
        btnSendKeep.isVisible = keepChecker.isKeepAvailable()

        // Observe dirty-state and tint the panel accordingly
        coroutineScope.launch {
            dirtyTracker.isDirty.collectLatest { dirty ->
                if (dirty) {
                    panel.setBackgroundColor(dirtyBackground)
                } else {
                    panel.background = originalBackground
                }
            }
        }
    }

    /**
     * Call when entering edit mode to bind the tracker and reset dirty state.
     */
    fun onEnterEditMode(editText: EditText, initialText: String) {
        dirtyTracker.markClean(initialText)
        dirtyTracker.attach(editText)
    }

    /**
     * Call when exiting edit mode to detach the tracker and reset the tint.
     */
    fun onExitEditMode() {
        dirtyTracker.detach()
        panel.background = originalBackground
    }
}
