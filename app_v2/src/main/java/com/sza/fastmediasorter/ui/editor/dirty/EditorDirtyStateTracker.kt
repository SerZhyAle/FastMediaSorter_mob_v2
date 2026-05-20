package com.sza.fastmediasorter.ui.editor.dirty

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * S0189 (Phase 09): observes a [StateFlow] of editor content and emits whether it diverges
 * from the [baseline]. Pure-Kotlin, content-agnostic: text editor passes a `StateFlow<String>`,
 * future drawing editor passes a `StateFlow<DrawingSnapshot>` (or its hash).
 *
 * Use [rebaseline] to mark the current content as the new clean point (e.g. after a save).
 */
class EditorDirtyStateTracker<T>(
    private val contentFlow: StateFlow<T>,
    initialBaseline: T,
) {

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty

    private var baseline: T = initialBaseline

    /**
     * Start observing [contentFlow]. The collector lifecycle is tied to [scope]; call
     * `scope.cancel()` to detach.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            contentFlow.collectLatest { value ->
                val dirty = value != baseline
                if (_isDirty.value != dirty) {
                    _isDirty.value = dirty
                }
            }
        }
    }

    /** Mark the current content as clean. Subsequent changes will flip [isDirty]. */
    fun rebaseline(newBaseline: T = contentFlow.value) {
        baseline = newBaseline
        _isDirty.value = (contentFlow.value != baseline)
    }
}
