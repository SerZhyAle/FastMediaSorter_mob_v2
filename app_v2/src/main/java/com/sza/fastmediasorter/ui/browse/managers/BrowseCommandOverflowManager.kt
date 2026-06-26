package com.sza.fastmediasorter.ui.browse.managers

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding

/**
 * Adaptive priority+overflow controller for the Browse top command bar (S0374).
 *
 * Measures the bar container, runs [allocateCommandBar], force-hides the commands that do not
 * fit (push model), and exposes the overflow set so [ResourceOpsMenuManager] can surface those
 * commands in the "⋮" menu. The horizontal-scroll fallback is gone: a command is either on the
 * bar or in the menu, never clipped.
 *
 * Feature-gated commands (mic, play-random, create-*) are eligible only when their owning
 * collector reports so via [setFeatureEligibility]. Always-eligible commands (back, sort, filter,
 * refresh, toggle-view, select/deselect, play) participate unconditionally. Eligibility is held
 * authoritatively here rather than inferred from view visibility, which our own overflow hiding
 * would otherwise contaminate.
 */
class BrowseCommandOverflowManager(
    private val binding: ActivityBrowseBinding
) {

    /** Ids currently pushed off the bar into the overflow menu. */
    private val overflowedIds = mutableSetOf<Int>()

    /** Cached measured widths (incl. horizontal margins), captured while a bar cell was visible. */
    private val widthCache = mutableMapOf<Int, Int>()

    /** Authoritative eligibility for runtime-gated commands, set by their owning collectors. */
    private val featureGatedEligible = mutableMapOf<Int, Boolean>()

    /** Invoked after the visible/overflow partition changes (focus-chain repair hook). */
    var onOverflowChanged: (() -> Unit)? = null

    private var recomputing = false

    fun isOverflowed(viewId: Int): Boolean = viewId in overflowedIds

    /**
     * Report whether a runtime-gated command is currently feature-eligible. Does not recompute -
     * the caller pairs this with [recompute] after asserting the button's base visibility.
     */
    fun setFeatureEligibility(viewId: Int, eligible: Boolean) {
        featureGatedEligible[viewId] = eligible
    }

    /** Recompute the visible/overflow partition after layout settles. */
    fun recompute() {
        if (recomputing) return
        recomputing = true
        binding.layoutControls.post {
            try {
                applyPartition()
            } finally {
                recomputing = false
            }
        }
    }

    private fun applyPartition() {
        val eligible = candidates().filter { c ->
            if (c.barView == null) return@filter false
            if (c.alwaysEligible) true else featureGatedEligible[c.menuId] == true
        }

        val available = binding.layoutControls.let {
            it.width - it.paddingStart - it.paddingEnd
        }
        val reserved = binding.btnResourceOps?.let { measuredWidthOf(it) } ?: 0

        val slots = eligible.map { c ->
            CommandSlot(
                viewId = c.menuId,
                measuredWidthPx = measuredWidthOf(c.barView!!),
                priority = c.priority
            )
        }

        val allocation = allocateCommandBar(slots, available, reserved)
        val previous = overflowedIds.toSet()

        eligible.forEach { c ->
            val view = c.barView ?: return@forEach
            val shouldBeVisible = c.menuId in allocation.visibleIds
            if (view.isVisible != shouldBeVisible) {
                view.isVisible = shouldBeVisible
            }
        }

        overflowedIds.clear()
        overflowedIds.addAll(allocation.overflowIds)

        if (overflowedIds != previous) {
            onOverflowChanged?.invoke()
        }
    }

    private fun measuredWidthOf(view: View): Int {
        val margins = (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.marginStart + it.marginEnd
        } ?: 0

        val live = view.measuredWidth
        if (view.isVisible && live > 0) {
            val total = live + margins
            widthCache[view.id] = total
            return total
        }
        widthCache[view.id]?.let { return it }

        // Never measured while visible: force a one-off measure.
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val measured = view.measuredWidth + margins
        if (measured > 0) widthCache[view.id] = measured
        return measured
    }

    /**
     * Priority-ordered candidate commands (index 0 = highest priority, overflows last).
     * btnResourceOps is excluded: it is the always-present overflow anchor (its width is reserved).
     */
    private fun candidates(): List<Candidate> {
        val filterCell = binding.btnFilter.parent as? View
        var priority = 0
        fun next() = priority++
        return listOf(
            Candidate(R.id.btnBack, binding.btnBack, next(), alwaysEligible = true),
            Candidate(R.id.btnSort, binding.btnSort, next(), alwaysEligible = true),
            Candidate(R.id.btnFilter, filterCell, next(), alwaysEligible = true),
            Candidate(R.id.btnRefresh, binding.btnRefresh, next(), alwaysEligible = true),
            // Feature-gated, not always-eligible: audio-only libraries force list mode, so the toggle
            // must vanish entirely (GONE) rather than linger disabled. Eligibility is reported from
            // BrowseStateUiUpdater per resource; an always-eligible toggle would be force-shown here,
            // overriding the GONE and leaving the dimmed-but-visible artifact.
            Candidate(R.id.btnToggleView, binding.btnToggleView, next(), alwaysEligible = false),
            Candidate(R.id.btnSelectAll, binding.btnSelectAll, next(), alwaysEligible = true),
            Candidate(R.id.btnDeselectAll, binding.btnDeselectAll, next(), alwaysEligible = true),
            Candidate(R.id.btnPlay, binding.btnPlay, next(), alwaysEligible = true),
            Candidate(R.id.btnPlayRandom, binding.btnPlayRandom, next(), alwaysEligible = false),
            Candidate(R.id.btnMicRecord, binding.btnMicRecord, next(), alwaysEligible = false),
            Candidate(R.id.btnCreateFolder, binding.btnCreateFolder, next(), alwaysEligible = false),
            Candidate(R.id.btnCreateTextFile, binding.btnCreateTextFile, next(), alwaysEligible = false),
            Candidate(R.id.btnCreateDrawing, binding.btnCreateDrawing, next(), alwaysEligible = false)
        )
    }

    private data class Candidate(
        val menuId: Int,
        val barView: View?,
        val priority: Int,
        val alwaysEligible: Boolean
    )
}
