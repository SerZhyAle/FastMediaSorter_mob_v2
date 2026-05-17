package com.sza.fastmediasorter.ui.player.helpers

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * S0189: tracks whether the EditText content has diverged from the last clean snapshot.
 *
 * Call [markClean] when entering edit mode (with the initial text).
 * Observe [isDirty] to drive the action-panel dirty-state indicator.
 */
class TextEditorDirtyStateTracker {

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty

    private var cleanSnapshot: String = ""
    private var editText: EditText? = null

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            val dirty = s?.toString() != cleanSnapshot
            if (_isDirty.value != dirty) {
                _isDirty.value = dirty
                Timber.d("S0189: TextEditorDirtyStateTracker dirty=$dirty")
            }
        }
    }

    fun markClean(initialText: String) {
        cleanSnapshot = initialText
        _isDirty.value = false
    }

    fun markDirty() {
        if (!_isDirty.value) {
            _isDirty.value = true
            Timber.d("S0189: TextEditorDirtyStateTracker dirty=true (forced)")
        }
    }

    fun attach(editText: EditText) {
        detach()
        this.editText = editText
        editText.addTextChangedListener(watcher)
    }

    fun detach() {
        editText?.removeTextChangedListener(watcher)
        editText = null
    }
}
