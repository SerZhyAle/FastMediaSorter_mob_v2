package com.sza.fastmediasorter.ui.common

import android.view.View
import android.view.ViewGroup

/**
 * Hides sibling views that share the host container with an overlay fragment.
 *
 * When a fragment is added to android.R.id.content via [replace][androidx.fragment.app.FragmentTransaction.replace]
 * or [add][androidx.fragment.app.FragmentTransaction.add], the host activity's original content view
 * (installed by setContentView) stays in the container as a non-fragment sibling. It is never removed
 * because the FragmentManager only manages fragments, not regular views. Those hidden views remain
 * focusable, so D-pad directional focus search escapes the overlay into them (S2899).
 *
 * [hideSiblings] makes every sibling of [overlayRoot] that is still visible INVISIBLE, which removes
 * them from focus search while preserving their layout. [restore] reverses it when the overlay is
 * dismissed so the underlying screen works normally again.
 */
object OverlayFocusTrap {

    /**
     * Hides all siblings of [overlayRoot] in its parent [ViewGroup] that are currently visible.
     * Returns the hidden views to pass to [restore]; empty if [overlayRoot] has no parent yet.
     */
    fun hideSiblings(overlayRoot: View): List<View> {
        val parent = overlayRoot.parent as? ViewGroup ?: return emptyList()
        val hidden = mutableListOf<View>()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) ?: continue
            if (child !== overlayRoot && child.visibility == View.VISIBLE) {
                child.visibility = View.INVISIBLE
                hidden.add(child)
            }
        }
        return hidden
    }

    /**
     * Restores visibility of every view hidden by [hideSiblings]. Safe to call when the
     * host activity is already gone: the view references are held, so visibility resets
     * without a lookup, and a dead view simply never gets relaid out.
     */
    fun restore(hidden: List<View>) {
        hidden.forEach { it.visibility = View.VISIBLE }
    }
}
