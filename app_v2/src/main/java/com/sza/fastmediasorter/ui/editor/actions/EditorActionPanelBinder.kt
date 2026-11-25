package com.sza.fastmediasorter.ui.editor.actions

import android.view.Menu
import android.view.View
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
 * [DirtyToolbarTinter]. Send to.. / Calculator are appended to the host screen's overflow
 * menu via [addOverflowItems]; Save, Save & close and Cancel stay as direct buttons.
 *
 * S4864: Save (floppy, stays in the editor) and Save & close (check mark, leaves it) are two
 * distinct toolbar buttons; with one shared floppy icon the in-place Save was hidden in the menu.
 *
 * S4676: the panel's own "more" button stays hidden - one screen carries one "more" menu.
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
    private var callbacks: EditorActionCallbacks? = null

    override fun setup(callbacks: EditorActionCallbacks) {
        this.callbacks = callbacks
        buttons.save.setOnClickListener {
            Timber.d("S4864: editor toolbar save tapped")
            callbacks.onSave()
        }
        buttons.saveClose.setOnClickListener { callbacks.onSaveAndClose() }
        buttons.cancel.setOnClickListener { callbacks.onCancel() }

        buttons.save.isVisible = true
        buttons.saveSend.isVisible = false
        buttons.sendKeep.isVisible = false
        buttons.more.isVisible = false

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

    override fun addOverflowItems(menu: Menu) {
        val target = callbacks ?: return
        val context = hostView.context
        // S0459: single unified outbound entry; the receiver list self-gates (incl. Keep).
        menu.add(Menu.NONE, MENU_SEND_TO, ORDER_FIRST, context.getString(R.string.share_to_menu_title))
            .setIcon(R.drawable.ic_send_plane)
            .setOnMenuItemClickListener {
                target.onSendTo()
                true
            }
        if (calculatorEnabled.value) {
            menu.add(Menu.NONE, MENU_CALCULATOR, ORDER_FIRST, context.getString(R.string.calculator_title))
                .setIcon(R.drawable.ic_calculator)
                .setOnMenuItemClickListener {
                    target.onOpenCalculator()
                    true
                }
        }
    }

    private companion object {
        // Outside the aapt R.id range (0x7f......), so they never collide with a host menu's own items.
        const val MENU_SEND_TO = 0x46760002
        const val MENU_CALCULATOR = 0x46760004

        // Equal orders keep insertion order; the lowest order puts the editor's actions on top.
        const val ORDER_FIRST = 0
    }
}
