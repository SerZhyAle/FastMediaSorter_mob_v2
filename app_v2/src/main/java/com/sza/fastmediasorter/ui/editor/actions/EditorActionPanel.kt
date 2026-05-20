package com.sza.fastmediasorter.ui.editor.actions

import android.widget.ImageButton

/**
 * S0189 (Phase 09): the 5-action editor panel contract shared by every in-app editor
 * (text notes - S0189, drawings - S0191, …).
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
 * Five mandatory callbacks; each fires on the UI thread when the matching button is pressed.
 *
 * - [onSave]          Save in place, stay in editor.
 * - [onSaveAndClose]  Save and finish the editor activity.
 * - [onSaveAndSend]   Save then surface a system share chooser for the saved file.
 * - [onSendToKeep]    Surface a text-only share targeted at the Google Keep app (text-note path only).
 * - [onCancel]        Discard changes; for staged new notes the staging file is deleted.
 */
data class EditorActionCallbacks(
    val onSave: () -> Unit,
    val onSaveAndClose: () -> Unit,
    val onSaveAndSend: () -> Unit,
    val onSendToKeep: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * Resolved bundle of the 5 [ImageButton] views the binder needs.
 *
 * Layout consumers populate this from `safeViews.btnEditorSave / SaveClose / SaveSend / SendKeep / Cancel`.
 */
data class EditorActionButtons(
    val save: ImageButton,
    val saveClose: ImageButton,
    val saveSend: ImageButton,
    val sendKeep: ImageButton,
    val cancel: ImageButton,
)
