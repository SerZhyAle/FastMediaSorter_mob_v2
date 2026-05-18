package com.sza.fastmediasorter.ui.editor.actions

import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.ui.editor.dirty.DirtyToolbarTinter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * S0189 (Phase 09): default [EditorActionPanel] implementation.
 *
 * Wires the 5 [EditorActionButtons], hides the "send to Keep" button when [keepAvailable]
 * is `false`, and delegates dirty-state tinting of [hostView] to [DirtyToolbarTinter].
 *
 * The host view is normally the editor toolbar that owns the buttons; the tint serves as
 * a single, very visible "you have unsaved changes" indicator. Dirty state is observed via
 * the caller-supplied [isDirty] flow - the binder is content-agnostic.
 */
class EditorActionPanelBinder(
    private val buttons: EditorActionButtons,
    private val hostView: View,
    private val keepAvailable: Boolean,
    private val isDirty: StateFlow<Boolean>,
    private val coroutineScope: CoroutineScope,
    private val cleanColor: Int,
    private val dirtyColor: Int,
) : EditorActionPanel {

    private val tinter = DirtyToolbarTinter(hostView, cleanColor, dirtyColor)

    override fun setup(callbacks: EditorActionCallbacks) {
        Timber.d("S0189: EditorActionPanelBinder.setup")
        buttons.save.setOnClickListener { callbacks.onSave() }
        buttons.saveClose.setOnClickListener { callbacks.onSaveAndClose() }
        buttons.saveSend.setOnClickListener { callbacks.onSaveAndSend() }
        buttons.sendKeep.setOnClickListener { callbacks.onSendToKeep() }
        buttons.cancel.setOnClickListener { callbacks.onCancel() }

        // Hide "send to Keep" when Keep is not installed; the rest stay always-visible.
        buttons.sendKeep.isVisible = keepAvailable

        // Observe dirty state and tint the host view accordingly.
        tinter.attach(coroutineScope, isDirty)
    }

    override fun onEnterEditMode() {
        // No-op for the binder itself - content-specific tracker bookkeeping happens
        // in the caller. Reserved so future editors can clear residual tint state here.
        tinter.resetToClean()
    }

    override fun onExitEditMode() {
        tinter.resetToClean()
    }
}
