package com.sza.fastmediasorter.ui.settings.revised.helpers

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.sza.fastmediasorter.R

class RevisedOperationsSectionBinder(
    private val root: View,
) {

    fun applyRevisedLayout() {
        reorderPrimarySlices()
    }

    fun ensureSectionExpanded(sectionId: String) {
        when (sectionId) {
            "operations", "safety" -> ensureExpanded(R.id.headerSafety, R.id.containerSafety)
            "copy_move" -> ensureExpanded(R.id.headerCopyMove, R.id.containerFileOperations)
            "destinations" -> ensureExpanded(R.id.headerDestinations, R.id.containerDestinations)
            "scheduled" -> ensureExpanded(R.id.headerScheduled, R.id.containerScheduled)
        }
    }

    private fun reorderPrimarySlices() {
        val contentRoot = contentRoot() ?: return

        // The revised Operations tab elevates scheduled automation above destination maintenance so
        // Browse users can see the management surface earlier instead of reading the legacy order.
        val orderedViews = listOfNotNull(
            sectionContainer(R.id.headerSafety),
            sectionContainer(R.id.headerScheduled),
            sectionContainer(R.id.headerCopyMove),
            sectionContainer(R.id.headerDestinations),
        ).distinct()

        orderedViews.forEach { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        orderedViews.forEach { view ->
            contentRoot.addView(view)
        }
    }

    private fun ensureExpanded(headerId: Int, contentId: Int) {
        val content = root.findViewById<View>(contentId) ?: return
        if (content.isVisible) return
        root.findViewById<TextView>(headerId)?.performClick()
    }

    private fun contentRoot(): ViewGroup? {
        return (root as? ViewGroup)?.getChildAt(0) as? ViewGroup
    }

    private fun sectionContainer(headerId: Int): View? {
        val header = root.findViewById<View>(headerId) ?: return null
        var current: View? = header
        while (current != null) {
            if (current is MaterialCardView) {
                return current
            }
            current = current.parent as? View
        }
        return null
    }
}