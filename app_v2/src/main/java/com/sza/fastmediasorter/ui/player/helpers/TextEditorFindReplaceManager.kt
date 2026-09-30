package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.text.Editable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.common.showSoftInputImplicitly
import timber.log.Timber

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
    private val matchSpans = mutableListOf<Any>()
    private val currentMatchSpans = mutableListOf<Any>()

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
        imm.showSoftInputImplicitly(safeViews.etFindQuery)
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
            highlightAllMatches()
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

        Timber.d("S3778: replace current")
        editable.replace(range.first, range.last + 1, replacement)
        performFindInEditor(safeViews.etFindQuery.text?.toString() ?: "")
    }

    /**
     * Replace all matches in reverse order to preserve indices, then re-run search.
     */
    fun replaceAll() {
        if (findMatches.isEmpty()) return
        val replacement = safeViews.etReplaceQuery.text?.toString() ?: ""
        val editable = safeViews.etTextContent.text ?: return
        val replacementRanges = mutableListOf<IntRange>()
        for (range in findMatches) {
            if (replacementRanges.isEmpty() || range.first > replacementRanges.last().last) {
                replacementRanges.add(range)
            }
        }
        val count = replacementRanges.size

        Timber.d("S3778: replace all")
        for (range in replacementRanges.asReversed()) {
            editable.replace(range.first, range.last + 1, replacement)
        }

        Toast.makeText(
            context,
            context.getString(R.string.replaced_n_occurrences, count),
            Toast.LENGTH_SHORT
        ).show()
        performFindInEditor(safeViews.etFindQuery.text?.toString() ?: "")
    }

    // ===== Internal helpers =====

    // The editor selection is not drawn while the query field holds focus, so matches are
    // painted with spans; span changes do not reach the undo/redo TextWatcher.
    private fun highlightAllMatches() {
        val editable = safeViews.etTextContent.text ?: return
        val color = ContextCompat.getColor(context, R.color.text_find_match_background)
        for (range in findMatches) {
            addSpan(editable, BackgroundColorSpan(color), range, matchSpans)
        }
    }

    private fun highlightFindMatch() {
        if (findCurrentIndex < 0 || findCurrentIndex >= findMatches.size) return
        val range = findMatches[findCurrentIndex]
        val editable = safeViews.etTextContent.text ?: return
        removeSpans(editable, currentMatchSpans)
        val background = ContextCompat.getColor(context, R.color.text_find_current_match_background)
        val foreground = ContextCompat.getColor(context, R.color.text_find_current_match_text)
        addSpan(editable, BackgroundColorSpan(background), range, currentMatchSpans)
        addSpan(editable, ForegroundColorSpan(foreground), range, currentMatchSpans)
        safeViews.etTextContent.setSelection(
            range.first.coerceAtMost(editable.length),
            (range.last + 1).coerceAtMost(editable.length)
        )
        scrollToOffset(range.first)
    }

    private fun scrollToOffset(offset: Int) {
        val editText = safeViews.etTextContent
        val layout = editText.layout ?: return
        val scrollView = editText.parent as? android.widget.ScrollView ?: return
        val line = layout.getLineForOffset(offset)
        // The ScrollView clips to its padding, so a hit scrolled to the bare line top hides under
        // it; one line of context above keeps the match fully visible.
        val lineHeight = layout.getLineBottom(line) - layout.getLineTop(line)
        val y = editText.top + layout.getLineTop(line) - scrollView.paddingTop - lineHeight
        scrollView.smoothScrollTo(0, y.coerceAtLeast(0))
    }

    private fun addSpan(editable: Editable, span: Any, range: IntRange, owner: MutableList<Any>) {
        val start = range.first.coerceIn(0, editable.length)
        val end = (range.last + 1).coerceIn(start, editable.length)
        if (start == end) return
        editable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        owner.add(span)
    }

    private fun removeSpans(editable: Editable, owner: MutableList<Any>) {
        owner.forEach { editable.removeSpan(it) }
        owner.clear()
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
        et.text?.let { editable ->
            removeSpans(editable, matchSpans)
            removeSpans(editable, currentMatchSpans)
        }
        if (et.hasSelection()) {
            et.setSelection(et.selectionEnd)
        }
    }
}
