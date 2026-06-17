package com.sza.fastmediasorter.ui.editor.actions

import android.widget.ImageButton

/**
 * S0189 (Phase 09): editor action panel contract shared by every in-app editor
 * (text notes - S0189, drawings - S0191, etc.).
 *
 * Concrete bindings live in [EditorActionPanelBinder]; consumers receive an instance via
 * [EditorActionPanelBinder.bind] and call [onEnterEditMode] / [onExitEditMode] around the
 * editor session.
 */
interface EditorActionPanel {

    /**
     * Wire click listeners and apply per-button visibility (e.g. hide "send to Keep"
     * when Keep is not installed). Must be called exactly once after the panel is built.
     */
    fun setup(callbacks: EditorActionCallbacks)

    /** Notify the panel that an edit session is starting (used to reset visuals). */
    fun onEnterEditMode()

    /** Notify the panel that the edit session ended; restores the clean tint. */
    fun onExitEditMode()
}

/**
 * Mandatory callbacks; each fires on the UI thread when the matching button is pressed.
 *
 * - [onSave]          Save in place, stay in editor.
 * - [onSaveAndClose]  Save and finish the editor activity.
 * - [onSendTo]        S0459: save, then open the unified «Send to..» menu for the current text.
 *                     Replaces the former separate Save & send / Send to Keep actions - Keep is now
 *                     one of the registry receivers offered inside that menu.
 * - [onOpenCalculator] Open the editor calculator round-trip command.
 * - [onCancel]        Discard changes; for staged new notes the staging file is deleted.
 */
data class EditorActionCallbacks(
    val onSave: () -> Unit,
    val onSaveAndClose: () -> Unit,
    val onSendTo: () -> Unit,
    val onOpenCalculator: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * Resolved bundle of [ImageButton] views the binder needs.
 *
 * Layout consumers populate this from `safeViews.btnEditorSave / SaveClose / SaveSend / SendKeep / More / Cancel`.
 */
data class EditorActionButtons(
    val save: ImageButton,
    val saveClose: ImageButton,
    val saveSend: ImageButton,
    val sendKeep: ImageButton,
    val more: ImageButton,
    val cancel: ImageButton,
)
