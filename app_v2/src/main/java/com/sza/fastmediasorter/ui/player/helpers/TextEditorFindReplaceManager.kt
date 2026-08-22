package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R

/**
 * Manages Find & Replace panel and editor toolbar actions for the inline text editor.
 * Handles: undo/redo button setup, find/replace panel lifecycle, case-insensitive search,
 * match navigation, single/bulk replacement, cursor position tracking.
 *
 * All view access is performed via [PlayerBindingSafeViews] to avoid NPE on detached views.
 */
class TextEditorFindReplaceManager(
    private val context: Context,
    private val safeViews: PlayerBindingSafeViews,
    private val undoRedoProvider: () -> TextUndoRedoManager?
) {

    // Find & Replace state
    private var findMatches = mutableListOf<IntRange>()
    private var findCurrentIndex = -1

    // ===== Editor toolbar setup =====

    /**
     * Bind all editor toolbar buttons (undo/redo/find/find-replace) and find-panel buttons.
     * Must be called once after views are inflated.
     */
    fun setupEditorToolbar() {
        safeViews.btnUndo.setOnClickListener {
            undoRedoProvider()?.undo()
        }
        safeViews.btnRedo.setOnClickListener {
            undoRedoProvider()?.redo()
        }
        safeViews.btnEditorFind.setOnClickListener {
            showFindPanel(withReplace = false)
        }
        safeViews.btnEditorFindReplace.setOnClickListener {
            showFindPanel(withReplace = true)
        }

        // Find panel action buttons
        safeViews.btnFindClose.setOnClickListener { closeFindPanel() }
        safeViews.btnFindNext.setOnClickListener { navigateFind(forward = true) }
        safeViews.btnFindPrev.setOnClickListener { navigateFind(forward = false) }
        safeViews.btnReplace.setOnClickListener { replaceCurrent() }
        safeViews.btnReplaceAll.setOnClickListener { replaceAll() }

        // Live search on query text change
        safeViews.etFindQuery.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable) {
                performFindInEditor(s.toString())
            }
        })
    }

    // ===== Cursor position tracking =====

    /**
     * Attach cursor-position tracking to the edit text.
     * Updates [safeViews.tvEditorCursorPos] on every cursor movement.
     */
    fun setupCursorPositionTracking() {
        safeViews.etTextContent.setAccessibilityDelegate(null)
        safeViews.etTextContent.post {
            updateCursorPosition()
        }
        safeViews.etTextContent.setOnClickListener { updateCursorPosition() }
        safeViews.etTextContent.accessibilityLiveRegion =
            android.view.View.ACCESSIBILITY_LIVE_REGION_NONE
    }

    fun updateCursorPosition() {
        val text = safeViews.etTextContent.text ?: return
        val pos = safeViews.etTextContent.selectionStart.coerceIn(0, text.length)
        val textBefore = text.subSequence(0, pos)
        val line = textBefore.count { it == '\n' } + 1
        val lastNewline = textBefore.lastIndexOf('\n')
        val col = if (lastNewline >= 0) pos - lastNewline else pos + 1
        safeViews.tvEditorCursorPos.text =
            context.getString(R.string.cursor_position, line, col)
    }

    // ===== Find panel lifecycle =====

    /**
     * Show the find (and optionally replace) panel, focus query input, show keyboard.
     */
    fun showFindPanel(withReplace: Boolean) {
        safeViews.textFindReplacePanel.isVisible = true
        safeViews.replaceRow.isVisible = withReplace
        safeViews.etFindQuery.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(safeViews.etFindQuery, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Close find/replace panel and clear all highlights and state.
     */
    fun closeFindPanel() {
        safeViews.textFindReplacePanel.isVisible = false
        safeViews.etFindQuery.setText("")
        safeViews.etReplaceQuery.setText("")
        safeViews.tvFindCounter.text = ""
        findMatches.clear()
        findCurrentIndex = -1
        clearEditorHighlights()
    }

    // ===== Search logic =====

    /**
     * Find all case-insensitive occurrences of [query] in EditText content.
     * Highlights first match and updates counter.
     */
    fun performFindInEditor(query: String) {
        findMatches.clear()
        findCurrentIndex = -1
        clearEditorHighlights()

        if (query.isEmpty()) {
            safeViews.tvFindCounter.text = ""
            return
        }

        val text = safeViews.etTextContent.text?.toString() ?: return
        val lowerQuery = query.lowercase()
        val lowerText = text.lowercase()

        var startIndex = 0
        while (true) {
            val found = lowerText.indexOf(lowerQuery, startIndex)
            if (found < 0) break
            findMatches.add(found until found + query.length)
            startIndex = found + 1
        }

        if (findMatches.isEmpty()) {
            safeViews.tvFindCounter.text = context.getString(R.string.find_no_results)
        } else {
            findCurrentIndex = 0
            highlightFindMatch()
            updateFindCounter()
        }
    }

    /**
     * Navigate to the next or previous find match.
     */
    fun navigateFind(forward: Boolean) {
        if (findMatches.isEmpty()) return
        findCurrentIndex = if (forward) {
            (findCurrentIndex + 1) % findMatches.size
        } else {
            (findCurrentIndex - 1 + findMatches.size) % findMatches.size
        }
        highlightFindMatch()
        updateFindCounter()
    }

    // ===== Replace logic =====

    /**
     * Replace the current match with replacement text, then re-run search.
     */
    fun replaceCurrent() {
        if (findCurrentIndex < 0 || findCurrentIndex >= findMatches.size) return
        val replacement = safeViews.etReplaceQuery.text?.toString() ?: ""
        val range = findMatches[findCurrentIndex]
        val editable = safeViews.etTextContent.text ?: return

        editable.replace(range.first, range.last, replacement)
        performFindInEditor(safeViews.etFindQuery.text?.toString() ?: "")
    }

    /**
     * Replace all matches in reverse order to preserve indices, then re-run search.
     */
    fun replaceAll() {
        if (findMatches.isEmpty()) return
        val replacement = safeViews.etReplaceQuery.text?.toString() ?: ""
        val editable = safeViews.etTextContent.text ?: return
        val count = findMatches.size

        for (range in findMatches.asReversed()) {
            editable.replace(range.first, range.last, replacement)
        }

        Toast.makeText(
            context,
            context.getString(R.string.replaced_n_occurrences, count),
            Toast.LENGTH_SHORT
        ).show()
        performFindInEditor(safeViews.etFindQuery.text?.toString() ?: "")
    }

    // ===== Internal helpers =====

    private fun highlightFindMatch() {
        if (findCurrentIndex < 0 || findCurrentIndex >= findMatches.size) return
        val range = findMatches[findCurrentIndex]
        val editText = safeViews.etTextContent
        editText.setSelection(
            range.first.coerceAtMost(editText.text.length),
            range.last.coerceAtMost(editText.text.length)
        )
        val layout = editText.layout ?: return
        val line = layout.getLineForOffset(range.first)
        val y = layout.getLineTop(line)
        (editText.parent as? android.widget.ScrollView)?.smoothScrollTo(0, y)
    }

    private fun updateFindCounter() {
        if (findMatches.isEmpty()) {
            safeViews.tvFindCounter.text = context.getString(R.string.find_no_results)
        } else {
            safeViews.tvFindCounter.text =
                context.getString(R.string.find_counter, findCurrentIndex + 1, findMatches.size)
        }
    }

    private fun clearEditorHighlights() {
        val et = safeViews.etTextContent
        if (et.hasSelection()) {
            et.setSelection(et.selectionEnd)
        }
    }
}
