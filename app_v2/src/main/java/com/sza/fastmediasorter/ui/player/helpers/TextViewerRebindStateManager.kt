package com.sza.fastmediasorter.ui.player.helpers

import androidx.core.view.isVisible
import com.sza.fastmediasorter.R

/**
 * Carries the text viewer's on-screen state across a host re-inflate (S1549 family).
 *
 * The re-inflated tree starts with the viewer and the editor containers `gone`, as the layout
 * declares them, and only a file load turns them on - so without this carry a rotation with a text
 * file open left the player black. [Snapshot] holds values only, never a view, so nothing of the
 * outgoing tree outlives the rebind.
 */
internal class TextViewerRebindStateManager {

    data class Snapshot(
        val visibility: Map<Int, Boolean>,
        val editorText: String?,
        val selectionStart: Int,
        val selectionEnd: Int,
    ) {
        val isViewerVisible: Boolean get() = visibility[R.id.textViewerContainer] == true
    }

    /** Read from the tree [safeViews] points at now - call before it is re-pointed. */
    fun capture(safeViews: PlayerBindingSafeViews): Snapshot {
        val visibility = carriedViews(safeViews).associate { (id, view) -> id to view.isVisible }
        val editorOpen = safeViews.textEditContainer.isVisible
        val editText = safeViews.etTextContent
        return Snapshot(
            visibility = visibility,
            editorText = if (editorOpen) editText.text.toString() else null,
            selectionStart = editText.selectionStart.coerceAtLeast(0),
            selectionEnd = editText.selectionEnd.coerceAtLeast(0),
        )
    }

    fun applyVisibility(safeViews: PlayerBindingSafeViews, snapshot: Snapshot) {
        carriedViews(safeViews).forEach { (id, view) ->
            snapshot.visibility[id]?.let { view.isVisible = it }
        }
    }

    private fun carriedViews(safeViews: PlayerBindingSafeViews) = listOf(
        R.id.textViewerContainer to safeViews.textViewerContainer,
        R.id.textScrollView to safeViews.textScrollView,
        R.id.textPageNavigation to safeViews.textPageNavigation,
        R.id.btnCopyTextCmd to safeViews.btnCopyTextCmd,
        R.id.btnEditTextCmd to safeViews.btnEditTextCmd,
        R.id.btnTranslateTextCmd to safeViews.btnTranslateTextCmd,
        R.id.btnSearchTextCmd to safeViews.btnSearchTextCmd,
    )
}
