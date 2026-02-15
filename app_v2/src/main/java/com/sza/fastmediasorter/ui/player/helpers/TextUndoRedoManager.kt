package com.sza.fastmediasorter.ui.player.helpers

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import timber.log.Timber

/**
 * Undo/Redo manager for EditText using command pattern.
 * Tracks text changes and maintains a history stack for undo/redo operations.
 *
 * Usage:
 *   val undoRedo = TextUndoRedoManager(editText, maxHistorySize = 50)
 *   undoRedo.attach()
 *   // ... user edits ...
 *   undoRedo.undo()
 *   undoRedo.redo()
 *   undoRedo.detach() // when done
 */
class TextUndoRedoManager(
    private val editText: EditText,
    private val maxHistorySize: Int = 50,
    private val onStateChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
) {

    private data class EditAction(
        val before: CharSequence,
        val after: CharSequence,
        val start: Int,
        val beforeCursorPos: Int,
        val afterCursorPos: Int
    )

    private val undoStack = ArrayDeque<EditAction>(maxHistorySize)
    private val redoStack = ArrayDeque<EditAction>()
    private var isUndoRedoInProgress = false
    private var textWatcher: TextWatcher? = null

    // Batch tracking: accumulate small changes into one action
    private var pendingBefore: CharSequence? = null
    private var pendingStart: Int = 0
    private var pendingBeforeCursor: Int = 0

    fun attach() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                if (isUndoRedoInProgress) return
                if (pendingBefore == null) {
                    pendingBefore = s.toString()
                    pendingStart = start
                    pendingBeforeCursor = editText.selectionStart
                }
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // No-op: we process in afterTextChanged
            }

            override fun afterTextChanged(s: Editable) {
                if (isUndoRedoInProgress) return
                val before = pendingBefore ?: return
                pendingBefore = null

                val action = EditAction(
                    before = before,
                    after = s.toString(),
                    start = pendingStart,
                    beforeCursorPos = pendingBeforeCursor,
                    afterCursorPos = editText.selectionStart
                )

                pushAction(action)
            }
        }
        textWatcher = watcher
        editText.addTextChangedListener(watcher)
        Timber.d("TextUndoRedoManager: Attached to EditText")
    }

    fun detach() {
        textWatcher?.let { editText.removeTextChangedListener(it) }
        textWatcher = null
        clear()
        Timber.d("TextUndoRedoManager: Detached")
    }

    fun undo() {
        if (undoStack.isEmpty()) return

        isUndoRedoInProgress = true
        try {
            val action = undoStack.removeLast()
            redoStack.addLast(action)

            editText.setText(action.before)
            val cursorPos = action.beforeCursorPos.coerceIn(0, editText.text.length)
            editText.setSelection(cursorPos)

            notifyStateChanged()
            Timber.d("TextUndoRedoManager: Undo (stack: ${undoStack.size} undo, ${redoStack.size} redo)")
        } finally {
            isUndoRedoInProgress = false
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return

        isUndoRedoInProgress = true
        try {
            val action = redoStack.removeLast()
            undoStack.addLast(action)

            editText.setText(action.after)
            val cursorPos = action.afterCursorPos.coerceIn(0, editText.text.length)
            editText.setSelection(cursorPos)

            notifyStateChanged()
            Timber.d("TextUndoRedoManager: Redo (stack: ${undoStack.size} undo, ${redoStack.size} redo)")
        } finally {
            isUndoRedoInProgress = false
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        pendingBefore = null
        notifyStateChanged()
    }

    private fun pushAction(action: EditAction) {
        // Trim undo stack to max size
        while (undoStack.size >= maxHistorySize) {
            undoStack.removeFirst()
        }
        undoStack.addLast(action)

        // New action clears redo stack
        redoStack.clear()

        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        onStateChanged?.invoke(canUndo(), canRedo())
    }
}
