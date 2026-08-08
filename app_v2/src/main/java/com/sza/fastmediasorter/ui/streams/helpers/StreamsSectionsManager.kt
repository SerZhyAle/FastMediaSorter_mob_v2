package com.sza.fastmediasorter.ui.streams.helpers

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.DisplayMode

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
 * Collapse redistributes vertical space by weight: a collapsed section drops to `wrap_content`
 * (header only) with weight 0, and the other section takes the remaining space. Collapsing one
 * section always expands the other, so at least one section's content is always visible. State is
 * in-memory only (MVP does not persist collapse across sessions).
 */
@UnstableApi
class StreamsSectionsManager(
    private val pinnedSection: View,
    private val pinnedHeader: View,
    private val pinnedChevron: ImageView,
    private val mainSection: View,
    private val mainHeader: View,
    private val mainChevron: ImageView,
    private val pinnedGridMode: StreamGridModeManager,
    private val mainGridMode: StreamGridModeManager,
) {

    private var pinnedCollapsed = false
    private var mainCollapsed = false

    init {
        pinnedHeader.setOnClickListener { toggle(isPinned = true) }
        mainHeader.setOnClickListener { toggle(isPinned = false) }
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

    fun onConfigurationChanged() {
        pinnedGridMode.onConfigurationChanged()
        mainGridMode.onConfigurationChanged()
    }

    fun stop() {
        pinnedGridMode.stop()
        mainGridMode.stop()
    }

    /**
     * Show/hide the pinned section and re-assert section sizing. When nothing is pinned there is no
     * top section to receive space, so any collapse state is reset to keep the main list expanded.
     */
    private fun updatePinnedVisibility(hasPinned: Boolean) {
        pinnedSection.isVisible = hasPinned
        if (!hasPinned) {
            pinnedCollapsed = false
            mainCollapsed = false
        }
        applySectionSizing()
    }

    // Collapsing a section always expands the other, so the two are never both collapsed. The main
    // section can only collapse while the pinned section is visible to take the freed space.
    private fun toggle(isPinned: Boolean) {
        if (isPinned) {
            if (pinnedCollapsed) {
                pinnedCollapsed = false
            } else {
                pinnedCollapsed = true
                mainCollapsed = false
            }
        } else {
            when {
                mainCollapsed -> mainCollapsed = false
                pinnedSection.isVisible -> {
                    mainCollapsed = true
                    pinnedCollapsed = false
                }
                else -> return
            }
        }
        applySectionSizing()
    }

    private fun applySectionSizing() {
        setSectionCollapsed(pinnedSection, pinnedCollapsed, WEIGHT_FILL)
        val mainWeight = if (pinnedCollapsed) WEIGHT_FILL else WEIGHT_MAIN
        setSectionCollapsed(mainSection, mainCollapsed, mainWeight)
        updateChevron(pinnedChevron, pinnedCollapsed)
        updateChevron(mainChevron, mainCollapsed)
    }

    private fun setSectionCollapsed(section: View, collapsed: Boolean, expandedWeight: Float) {
        val lp = section.layoutParams as? LinearLayout.LayoutParams ?: return
        if (collapsed) {
            lp.height = LinearLayout.LayoutParams.WRAP_CONTENT
            lp.weight = WEIGHT_NONE
        } else {
            lp.height = 0
            lp.weight = expandedWeight
        }
        section.layoutParams = lp
    }

    private fun updateChevron(chevron: ImageView, collapsed: Boolean) {
        chevron.setImageResource(if (collapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less)
        chevron.contentDescription = chevron.context.getString(
            if (collapsed) R.string.streams_section_expand else R.string.streams_section_collapse
        )
    }

    private companion object {
        // Default expanded split: pinned gets 1 share, main gets 2 (main larger). A sole-expanded
        // section uses WEIGHT_FILL; a collapsed section uses WEIGHT_NONE (wrap to its header).
        const val WEIGHT_FILL = 1f
        const val WEIGHT_MAIN = 2f
        const val WEIGHT_NONE = 0f
    }
}
