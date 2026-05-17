package com.sza.fastmediasorter.ui.editor.dirty

import android.graphics.drawable.Drawable
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * S0189 (Phase 09): applies a clean / dirty background colour to a toolbar [View] based on
 * an `isDirty` flow.
 *
 * On the first invocation it remembers the toolbar's original `background` drawable so the
 * clean state restores the *original* style (the toolbar's background is normally an attr
 * reference, not a solid colour — setting `Color.TRANSPARENT` would lose it).
 */
class DirtyToolbarTinter(
    private val toolbar: View,
    private val cleanColor: Int,
    private val dirtyColor: Int,
) {
    private val originalBackground: Drawable? = toolbar.background

    /**
     * Subscribe to [isDirtyFlow] and apply the matching tint each time the flag flips.
     * The subscription lifecycle is tied to [scope].
     */
    fun attach(scope: CoroutineScope, isDirtyFlow: StateFlow<Boolean>) {
        scope.launch {
            isDirtyFlow.collectLatest { dirty ->
                if (dirty) {
                    toolbar.setBackgroundColor(dirtyColor)
                } else {
                    if (cleanColor == TRANSPARENT_PRESERVE_ORIGINAL) {
                        toolbar.background = originalBackground
                    } else {
                        toolbar.setBackgroundColor(cleanColor)
                    }
                }
            }
        }
    }

    /** Restore the original toolbar background unconditionally (e.g. on exit edit mode). */
    fun resetToClean() {
        toolbar.background = originalBackground
    }

    companion object {
        /**
         * Sentinel for [cleanColor] requesting that the original drawable be restored
         * instead of a flat colour fill. Use this when the toolbar's clean state is a
         * styled `?attr/...` background rather than a single colour.
         */
        const val TRANSPARENT_PRESERVE_ORIGINAL: Int = 0
    }
}
