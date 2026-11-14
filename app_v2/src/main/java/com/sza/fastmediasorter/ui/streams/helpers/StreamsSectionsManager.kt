package com.sza.fastmediasorter.ui.streams.helpers

import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import timber.log.Timber

/**
 * S1141: coordinates the streams screen's two stacked sections - pinned (top) and main/unpinned
 * (bottom). Each section is a fully independent [StreamGridModeManager] over its own RecyclerView,
 * adapters and off-screen capture host, so the two lists scroll independently and stay visible at
 * once - a single RecyclerView cannot keep the pinned rows in view while the rest scrolls. This
 * manager owns the top-section auto-hide and the collapsible-header sizing; the shared display mode
 * is applied identically to both sections.
 *
 * S1502: it no longer splits the catalog. Filtering, ordering AND the pinned/unpinned split all
 * happen once in the ViewModel, off the main thread; both halves arrive here already separated.
 *
 * S4626: both headers are [CollapsibleSectionHeader]s registered with the shared
 * [CollapsibleSectionsManager], so the marker, the whole-header activation and the remembered state are the
 * widget's. Opening or closing one section never moves the other (COLLAPSIBLE-GROUP section 2 rule 3);
 * the sections share the height by weight, so an open section takes what a closed one leaves.
 *
 * A closed section drops to `wrap_content` (header only) with weight 0. The main header is a wrapper over
 * a single topic while neither the pinned section nor a sibling section is shown, so it is hidden and the
 * main body stays open then; the remembered state is kept and applies again once a second topic appears.
 */
@UnstableApi
class StreamsSectionsManager(
    sections: CollapsibleSectionsManager,
    private val pinnedSection: View,
    pinnedHeader: CollapsibleSectionHeader,
    pinnedBody: View,
    private val mainSection: View,
    private val mainHeader: CollapsibleSectionHeader,
    private val mainBody: View,
    private val pinnedGridMode: StreamGridModeManager,
    private val mainGridMode: StreamGridModeManager,
) {

    private var pinnedExpanded = true
    private var mainExpanded = true
    private var siblingTopicVisible = false

    init {
        sections.register(pinnedHeader, pinnedBody, KEY_PINNED, defaultExpanded = true) { expanded ->
            pinnedExpanded = expanded
            applySectionSizing()
        }
        sections.register(mainHeader, mainBody, KEY_MAIN, defaultExpanded = true) { expanded ->
            mainExpanded = expanded
            applySectionSizing()
        }
    }

    /** Apply a list/grid mode change to both sections from the already-split halves. */
    fun applyMode(mode: DisplayMode, pinned: List<StreamSourceEntity>, unpinned: List<StreamSourceEntity>) {
        updatePinnedVisibility(pinned.isNotEmpty())
        pinnedGridMode.applyMode(mode, pinned)
        mainGridMode.applyMode(mode, unpinned)
    }

    /** Push a catalog/filter change to both sections without a mode swap. */
    fun submitList(pinned: List<StreamSourceEntity>, unpinned: List<StreamSourceEntity>) {
        updatePinnedVisibility(pinned.isNotEmpty())
        pinnedGridMode.submitCurrentList(pinned)
        mainGridMode.submitCurrentList(unpinned)
    }

    /** Tells the manager whether a section outside its own two (the device broadcasts) is on screen. */
    fun setSiblingTopicVisible(visible: Boolean) {
        if (siblingTopicVisible == visible) return
        siblingTopicVisible = visible
        applyTopicLayout()
    }

    fun onConfigurationChanged() {
        pinnedGridMode.onConfigurationChanged()
        mainGridMode.onConfigurationChanged()
    }

    fun stop() {
        pinnedGridMode.stop()
        mainGridMode.stop()
    }

    private fun updatePinnedVisibility(hasPinned: Boolean) {
        pinnedSection.isVisible = hasPinned
        applyTopicLayout()
    }

    private fun hasSeveralTopics(): Boolean = pinnedSection.isVisible || siblingTopicVisible

    // COLLAPSIBLE-GROUP section 2 rule 2: no collapsible wrapper over a single coherent set of controls.
    private fun applyTopicLayout() {
        val severalTopics = hasSeveralTopics()
        mainHeader.isVisible = severalTopics
        mainBody.isVisible = !severalTopics || mainExpanded
        applySectionSizing()
    }

    private fun applySectionSizing() {
        Timber.d("S4626: streams sizing pinned=$pinnedExpanded main=$mainExpanded sibling=$siblingTopicVisible")
        setSectionOpen(pinnedSection, pinnedExpanded, WEIGHT_FILL)
        val mainOpen = !hasSeveralTopics() || mainExpanded
        val mainWeight = if (pinnedSection.isVisible && pinnedExpanded) WEIGHT_MAIN else WEIGHT_FILL
        setSectionOpen(mainSection, mainOpen, mainWeight)
    }

    private fun setSectionOpen(section: View, open: Boolean, openWeight: Float) {
        val lp = section.layoutParams as? LinearLayout.LayoutParams ?: return
        if (open) {
            lp.height = 0
            lp.weight = openWeight
        } else {
            lp.height = LinearLayout.LayoutParams.WRAP_CONTENT
            lp.weight = WEIGHT_NONE
        }
        section.layoutParams = lp
    }

    private companion object {
        const val KEY_PINNED = "streams__pinned"
        const val KEY_MAIN = "streams__main"

        // Default open split: pinned gets 1 share, main gets 2 (main larger). A sole open section
        // uses WEIGHT_FILL; a closed section uses WEIGHT_NONE (wrap to its header).
        const val WEIGHT_FILL = 1f
        const val WEIGHT_MAIN = 2f
        const val WEIGHT_NONE = 0f
    }
}
