package com.sza.fastmediasorter.ui.editor.actions

import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.editor.dirty.DirtyToolbarTinter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * S0189 (Phase 09): default [EditorActionPanel] implementation.
 *
 * Wires the [EditorActionButtons] and delegates dirty-state tinting of [hostView] to
 * [DirtyToolbarTinter]. Save / Send to.. / Calculator are exposed through the always-visible "more"
 * overflow menu; only Save & close and Cancel stay as direct buttons.
 *
 * S0459: the former separate "Save & send" and "Send to Keep" entries are merged into a single
 * "Send to.." item that routes through the unified menu (Keep is one of its receivers).
 *
 * The host view is normally the editor toolbar that owns the buttons; the tint serves as
 * a single, very visible "you have unsaved changes" indicator. Dirty state is observed via
 * the caller-supplied [isDirty] flow - the binder is content-agnostic.
 */
class EditorActionPanelBinder(
    private val buttons: EditorActionButtons,
    private val hostView: View,
    private val calculatorEnabled: StateFlow<Boolean>,
    private val isDirty: StateFlow<Boolean>,
    private val coroutineScope: CoroutineScope,
    private val cleanColor: Int,
    private val dirtyColor: Int,
) : EditorActionPanel {

    private val tinter = DirtyToolbarTinter(hostView, cleanColor, dirtyColor)

    override fun setup(callbacks: EditorActionCallbacks) {
        // Save / Send to.. / Calculator now live inside the overflow menu;
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
            // S0459: single unified outbound entry; the menu self-gates its receivers (incl. Keep).
            menu.add(0, MENU_SEND_TO, order++, R.string.share_to_menu_title)
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
                    MENU_SEND_TO -> {
                        callbacks.onSendTo()
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
        const val MENU_SEND_TO = 2
        const val MENU_CALCULATOR = 4
    }
}
