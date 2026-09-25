package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/** S3523: the screen edge the taskbar composition is anchored to, decoded from the stored placement token. */
enum class LauncherTaskbarEdge(val token: String) {
    BOTTOM(AppSettings.LAUNCHER_TASKBAR_PLACEMENT_BOTTOM),
    TOP(AppSettings.LAUNCHER_TASKBAR_PLACEMENT_TOP),
    LEFT(AppSettings.LAUNCHER_TASKBAR_PLACEMENT_LEFT),
    RIGHT(AppSettings.LAUNCHER_TASKBAR_PLACEMENT_RIGHT),
    ;

    /** A side edge draws the bar as a column; the two horizontal edges keep the pre-S3523 row. */
    val isVertical: Boolean get() = this == LEFT || this == RIGHT

    companion object {
        /** A token from a newer build, or none at all, degrades to the bottom edge like the store does. */
        fun fromToken(token: String?): LauncherTaskbarEdge = entries.firstOrNull { it.token == token } ?: BOTTOM
    }
}

/**
 * S1643: the one node that decides which screen edge the taskbar composition is anchored to.
 *
 * It moves the anchoring edge of the whole bar, never the layout of the elements inside it (strategic
 * ADR-1), so the Start button, the two icon strips and the tray keep their own visibility rules and their
 * own instances across a toggle. Nothing is re-inflated or re-bound here: a running gadget, a subscribed
 * indicator and a bound adapter all survive a placement change, which is what strategic §3.2 requires.
 * S3523 added the two side edges; the bar's inner orientation belongs to [LauncherTaskbarManager], which
 * receives the edge through [onEdge] before the constraints are rebuilt, so the cloned constraint set
 * already carries the bar's new thickness.
 *
 * The status strip keeps the very top of the screen in every placement (owner ruling, §6 item 1), so the
 * top and side placements start the bar directly under it rather than above it.
 */
class LauncherTaskbarPlacementManager(
    private val lifecycleOwner: LifecycleOwner,
    private val root: ConstraintLayout,
    private val onEdge: (LauncherTaskbarEdge) -> Unit = {},
) {

    /** Follow the stored placement for as long as [lifecycleOwner] is started. Call once from the host. */
    fun bind(placement: Flow<LauncherTaskbarEdge>) {
        lifecycleOwner.collectOnLifecycle(placement) { edge ->
            Timber.d("S3523: taskbar placement applied edge=$edge vertical=${edge.isVertical}")
            onEdge(edge)
            applyConstraints(edge)
            applyFocusOrder(edge)
        }
    }

    /**
     * Each anchor is cleared before it is set: a constraint left over from the previous placement would
     * fight the new one and leave the scroll container sized against both edges at once.
     */
    private fun applyConstraints(edge: LauncherTaskbarEdge) {
        val set = ConstraintSet().apply { clone(root) }
        ANCHORED_IDS.forEach { id -> SIDES.forEach { side -> set.clear(id, side) } }
        when (edge) {
            LauncherTaskbarEdge.BOTTOM -> anchorBottom(set)
            LauncherTaskbarEdge.TOP -> anchorTop(set)
            LauncherTaskbarEdge.LEFT, LauncherTaskbarEdge.RIGHT -> anchorSide(set, edge == LauncherTaskbarEdge.LEFT)
        }
        set.applyTo(root)
    }

    private fun anchorBottom(set: ConstraintSet) {
        set.connect(BAR, ConstraintSet.BOTTOM, PARENT, ConstraintSet.BOTTOM)
        set.connect(BAR, ConstraintSet.START, PARENT, ConstraintSet.START)
        set.connect(BAR, ConstraintSet.END, PARENT, ConstraintSet.END)
        set.connect(DOTS, ConstraintSet.BOTTOM, BAR, ConstraintSet.TOP)
        spanDotsBetween(set, PARENT, ConstraintSet.START, PARENT, ConstraintSet.END)
        set.connect(GRID, ConstraintSet.TOP, STRIP, ConstraintSet.BOTTOM)
        set.connect(GRID, ConstraintSet.BOTTOM, DOTS, ConstraintSet.TOP)
        spanGridBetween(set, PARENT, ConstraintSet.START, PARENT, ConstraintSet.END)
    }

    private fun anchorTop(set: ConstraintSet) {
        set.connect(BAR, ConstraintSet.TOP, STRIP, ConstraintSet.BOTTOM)
        set.connect(BAR, ConstraintSet.START, PARENT, ConstraintSet.START)
        set.connect(BAR, ConstraintSet.END, PARENT, ConstraintSet.END)
        // The dots keep the pre-S3523 anchor above the bar's top edge, which the S1643 top placement drew.
        set.connect(DOTS, ConstraintSet.BOTTOM, BAR, ConstraintSet.TOP)
        spanDotsBetween(set, PARENT, ConstraintSet.START, PARENT, ConstraintSet.END)
        set.connect(GRID, ConstraintSet.TOP, BAR, ConstraintSet.BOTTOM)
        set.connect(GRID, ConstraintSet.BOTTOM, PARENT, ConstraintSet.BOTTOM)
        spanGridBetween(set, PARENT, ConstraintSet.START, PARENT, ConstraintSet.END)
    }

    /** The desktop and its page dots take the width the column leaves, on the side away from [atLeft]'s edge. */
    private fun anchorSide(set: ConstraintSet, atLeft: Boolean) {
        val barEdge = if (atLeft) ConstraintSet.START else ConstraintSet.END
        set.connect(BAR, ConstraintSet.TOP, STRIP, ConstraintSet.BOTTOM)
        set.connect(BAR, ConstraintSet.BOTTOM, PARENT, ConstraintSet.BOTTOM)
        set.connect(BAR, barEdge, PARENT, barEdge)
        set.connect(DOTS, ConstraintSet.BOTTOM, PARENT, ConstraintSet.BOTTOM)
        set.connect(GRID, ConstraintSet.TOP, STRIP, ConstraintSet.BOTTOM)
        set.connect(GRID, ConstraintSet.BOTTOM, DOTS, ConstraintSet.TOP)
        if (atLeft) {
            spanDotsBetween(set, BAR, ConstraintSet.END, PARENT, ConstraintSet.END)
            spanGridBetween(set, BAR, ConstraintSet.END, PARENT, ConstraintSet.END)
        } else {
            spanDotsBetween(set, PARENT, ConstraintSet.START, BAR, ConstraintSet.START)
            spanGridBetween(set, PARENT, ConstraintSet.START, BAR, ConstraintSet.START)
        }
    }

    private fun spanDotsBetween(set: ConstraintSet, startId: Int, startSide: Int, endId: Int, endSide: Int) {
        set.connect(DOTS, ConstraintSet.START, startId, startSide)
        set.connect(DOTS, ConstraintSet.END, endId, endSide)
    }

    private fun spanGridBetween(set: ConstraintSet, startId: Int, startSide: Int, endId: Int, endSide: Int) {
        set.connect(GRID, ConstraintSet.START, startId, startSide)
        set.connect(GRID, ConstraintSet.END, endId, endSide)
    }

    /**
     * The bar's two buttons declare their D-pad exit toward the desktop in the layout, which can only name
     * one direction. The desktop is below the bar under the top placement and above it under the bottom one,
     * so the pair is reassigned here rather than left pointing at whichever edge the XML happened to assume.
     * A side edge is left alone: [LauncherTaskbarManager] clears the bar's explicit ids for a column, and the
     * geometric search then finds the desktop beside it.
     */
    private fun applyFocusOrder(edge: LauncherTaskbarEdge) {
        if (edge.isVertical) {
            return
        }
        val atTop = edge == LauncherTaskbarEdge.TOP
        val towardsDesktop = if (atTop) R.id.launcherDesktop else View.NO_ID
        val awayFromDesktop = if (atTop) View.NO_ID else R.id.launcherDesktop
        listOfNotNull(
            root.findViewById<View>(R.id.btnStart),
            root.findViewById<View>(R.id.btnAllApps),
        ).forEach { button ->
            button.nextFocusDownId = towardsDesktop
            button.nextFocusUpId = awayFromDesktop
        }
    }

    private companion object {
        val BAR = R.id.launcherTaskbar
        val GRID = R.id.launcherGridScroll
        val DOTS = R.id.launcherPageIndicator
        val STRIP = R.id.launcherStatusStrip
        const val PARENT = ConstraintSet.PARENT_ID

        /** Every view whose anchors this manager rewrites; each loses all four before the new set is drawn. */
        val ANCHORED_IDS = listOf(BAR, GRID, DOTS)
        val SIDES = listOf(ConstraintSet.TOP, ConstraintSet.BOTTOM, ConstraintSet.START, ConstraintSet.END)
    }
}
