package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * S1643: the one node that decides which screen edge the taskbar composition is anchored to.
 *
 * It moves the anchoring edge of the whole bar, never the layout of the elements inside it (strategic
 * ADR-1), so the Start button, the two icon strips and the tray keep their own visibility rules and their
 * own instances across a toggle. Nothing is re-inflated or re-bound here: a running gadget, a subscribed
 * indicator and a bound adapter all survive a placement change, which is what strategic §3.2 requires.
 *
 * The status strip keeps the very top of the screen in both placements (owner ruling, §6 item 1), so the
 * top placement puts the bar directly under it rather than above it.
 */
class LauncherTaskbarPlacementManager(
    private val lifecycleOwner: LifecycleOwner,
    private val root: ConstraintLayout,
) {

    /** Follow the stored placement for as long as [lifecycleOwner] is started. Call once from the host. */
    fun bind(placementAtTop: Flow<Boolean>) {
        lifecycleOwner.collectOnLifecycle(placementAtTop) { atTop ->
            applyConstraints(atTop)
            applyFocusOrder(atTop)
        }
    }

    /**
     * Each replaced anchor is cleared before its opposite is set: a constraint left over from the previous
     * placement would fight the new one and leave the scroll container sized against both edges at once.
     */
    private fun applyConstraints(atTop: Boolean) {
        val set = ConstraintSet().apply { clone(root) }
        if (atTop) {
            set.clear(R.id.launcherTaskbar, ConstraintSet.BOTTOM)
            set.connect(
                R.id.launcherTaskbar,
                ConstraintSet.TOP,
                R.id.launcherStatusStrip,
                ConstraintSet.BOTTOM,
            )
            set.clear(R.id.launcherGridScroll, ConstraintSet.TOP)
            set.connect(
                R.id.launcherGridScroll,
                ConstraintSet.TOP,
                R.id.launcherTaskbar,
                ConstraintSet.BOTTOM,
            )
            set.clear(R.id.launcherGridScroll, ConstraintSet.BOTTOM)
            set.connect(
                R.id.launcherGridScroll,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
            )
        } else {
            set.clear(R.id.launcherTaskbar, ConstraintSet.TOP)
            set.connect(
                R.id.launcherTaskbar,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
            )
            set.clear(R.id.launcherGridScroll, ConstraintSet.TOP)
            set.connect(
                R.id.launcherGridScroll,
                ConstraintSet.TOP,
                R.id.launcherStatusStrip,
                ConstraintSet.BOTTOM,
            )
            set.clear(R.id.launcherGridScroll, ConstraintSet.BOTTOM)
            set.connect(
                R.id.launcherGridScroll,
                ConstraintSet.BOTTOM,
                R.id.launcherTaskbar,
                ConstraintSet.TOP,
            )
        }
        set.applyTo(root)
    }

    /**
     * The bar's two buttons declare their D-pad exit toward the desktop in the layout, which can only name
     * one direction. The desktop is below the bar under the top placement and above it under the bottom one,
     * so the pair is reassigned here rather than left pointing at whichever edge the XML happened to assume.
     */
    private fun applyFocusOrder(atTop: Boolean) {
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
}
