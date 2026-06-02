package com.sza.fastmediasorter.ui.player.helpers

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import com.sza.fastmediasorter.R

/**
 * Floating selection ActionMode callback for the editable text editor (EditText).
 *
 * Leaves the platform Cut / Copy / Paste / Select all / Search / Share items untouched and
 * appends a single "Calculator" item when the calculator feature is enabled. Tapping it opens
 * the calculator seeded with the current selection; the computed result is inserted after the
 * selection by the editor's existing calculator result handler ([TextViewerManager.insertCalculatorResult]).
 *
 * @param isCalculatorAvailable Re-evaluated on every selection so a settings change takes effect
 *                              without re-wiring the callback.
 * @param getSelectedText       Synchronous supplier of the currently selected text.
 * @param onOpenCalculator      Called with the selected text when the user taps "Calculator".
 */
class EditorSelectionActionModeCallback(
    private val isCalculatorAvailable: () -> Boolean,
    private val getSelectedText: () -> String,
    private val onOpenCalculator: (String) -> Unit,
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        if (isCalculatorAvailable()) {
            menu.add(Menu.NONE, MENU_CALCULATOR, Menu.NONE, R.string.calculator_title)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != MENU_CALCULATOR) return false
        val text = getSelectedText()
        if (text.isBlank()) return false
        onOpenCalculator(text)
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {}

    private companion object {
        const val MENU_CALCULATOR = 1
    }
}
