package com.sza.fastmediasorter.ui.settings.revised.helpers

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.sza.fastmediasorter.R

class RevisedGeneralSectionBinder(
    private val root: View,
) {

    fun applyRevisedLayout() {
        applyLegacyOnlyVisibility()
        reorderPrimarySlices()
    }

    fun ensureSectionExpanded(sectionId: String) {
        when (sectionId) {
            "general", "interface" -> ensureExpanded(R.id.headerInterface, R.id.containerInterface)
            "app_data" -> ensureExpanded(R.id.headerAppData, R.id.containerAppData)
            "system" -> ensureExpanded(R.id.headerSystem, R.id.containerSystem)
            "about", "help", "legal" -> ensureSiblingExpanded(R.id.headerAbout)
        }
    }

    private fun applyLegacyOnlyVisibility() {
        // S0125 keeps the Google Account card, Reset All, and debug-only controls on the
        // untouched legacy host until their dedicated follow-up parity work lands.
        root.findViewById<View>(R.id.cardGoogleAccount)?.isVisible = false
        root.findViewById<View>(R.id.btnResetSettings)?.isVisible = false
        sectionContainer(R.id.headerDebugSettings)?.isVisible = false
        root.findViewById<View>(R.id.headerDebugSettings)?.isVisible = false
        root.findViewById<View>(R.id.containerDebugSettings)?.isVisible = false
    }

    private fun reorderPrimarySlices() {
        val contentRoot = contentRoot() ?: return

        // The revised General tab surfaces long-lived preferences before app-data actions so the
        // second host is visibly regrouped instead of reading like the legacy tab in a new shell.
        val orderedViews = listOfNotNull(
            sectionContainer(R.id.headerInterface),
            sectionContainer(R.id.headerSystem),
            sectionContainer(R.id.headerAppData),
            root.findViewById(R.id.btnPermissionsManagement),
            sectionContainer(R.id.headerAbout),
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

    private fun ensureSiblingExpanded(headerId: Int) {
        val header = root.findViewById<TextView>(headerId) ?: return
        val parent = header.parent as? ViewGroup ?: return
        val headerIndex = parent.indexOfChild(header)
        val content = parent.getChildAt(headerIndex + 1) ?: return
        if (content.isVisible) return
        header.performClick()
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