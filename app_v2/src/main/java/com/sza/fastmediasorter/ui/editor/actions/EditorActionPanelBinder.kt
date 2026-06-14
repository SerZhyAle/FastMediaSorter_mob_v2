package com.sza.fastmediasorter.ui.editor.actions

import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.editor.dirty.DirtyToolbarTinter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * S0189 (Phase 09): default [EditorActionPanel] implementation.
 *
 * Wires the [EditorActionButtons] and delegates dirty-state tinting of [hostView] to
 * [DirtyToolbarTinter]. Save / Save & send / Send to Keep / Calculator are exposed through the
 * always-visible "more" overflow menu; only Save & close and Cancel stay as direct buttons.
 *
 * The host view is normally the editor toolbar that owns the buttons; the tint serves as
 * a single, very visible "you have unsaved changes" indicator. Dirty state is observed via
 * the caller-supplied [isDirty] flow - the binder is content-agnostic.
 */
class EditorActionPanelBinder(
    private val buttons: EditorActionButtons,
    private val hostView: View,
    private val keepAvailable: Boolean,
    private val calculatorEnabled: StateFlow<Boolean>,
    private val isDirty: StateFlow<Boolean>,
    private val coroutineScope: CoroutineScope,
    private val cleanColor: Int,
    private val dirtyColor: Int,
) : EditorActionPanel {

    private val tinter = DirtyToolbarTinter(hostView, cleanColor, dirtyColor)

    override fun setup(callbacks: EditorActionCallbacks) {
        // Save / Save & send / Send to Keep / Calculator now live inside the overflow menu;
        // only Save & close and Cancel remain as direct buttons on the panel.
        buttons.saveClose.setOnClickListener { callbacks.onSaveAndClose() }
        buttons.more.setOnClickListener { showOverflowMenu(callbacks) }
        buttons.cancel.setOnClickListener { callbacks.onCancel() }

        // The moved actions are hidden as standalone buttons; the overflow menu is always present.
        buttons.save.isVisible = false
        buttons.saveSend.isVisible = false
        buttons.sendKeep.isVisible = false
        buttons.more.isVisible = true

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

    private fun showOverflowMenu(callbacks: EditorActionCallbacks) {
        PopupMenu(buttons.more.context, buttons.more).apply {
            var order = 0
            menu.add(0, MENU_SAVE, order++, R.string.text_editor_action_save)
            menu.add(0, MENU_SAVE_SEND, order++, R.string.text_editor_action_save_send)
            // Keep entry only when the Google Keep app is installed.
            Timber.d("S0362: text editor overflow opened, keepAvailable=$keepAvailable")
            if (keepAvailable) {
                menu.add(0, MENU_SEND_KEEP, order++, R.string.text_editor_action_send_keep)
            }
            // Calculator entry only when the feature is enabled in settings.
            if (calculatorEnabled.value) {
                menu.add(0, MENU_CALCULATOR, order++, R.string.calculator_title)
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_SAVE -> {
                        callbacks.onSave()
                        true
                    }
                    MENU_SAVE_SEND -> {
                        callbacks.onSaveAndSend()
                        true
                    }
                    MENU_SEND_KEEP -> {
                        callbacks.onSendToKeep()
                        true
                    }
                    MENU_CALCULATOR -> {
                        callbacks.onOpenCalculator()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private companion object {
        const val MENU_SAVE = 1
        const val MENU_SAVE_SEND = 2
        const val MENU_SEND_KEEP = 3
        const val MENU_CALCULATOR = 4
    }
}
