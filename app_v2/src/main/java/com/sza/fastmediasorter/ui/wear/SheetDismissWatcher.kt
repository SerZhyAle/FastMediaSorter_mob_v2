package com.sza.fastmediasorter.ui.wear

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * S1735: tells the host window that its sheet is gone.
 *
 * A `BottomSheetDialogFragment` dismissed by a swipe or by the scrim does not touch the back stack, so the
 * back-stack listener alone never fires and the empty window would stay on screen with nothing in it.
 * Watching the fragment's destruction covers the swipe; the back-stack listener covers the rest.
 */
class SheetDismissWatcher(
    private val onSheetGone: () -> Unit
) : FragmentManager.FragmentLifecycleCallbacks() {

    override fun onFragmentDetached(fragmentManager: FragmentManager, fragment: Fragment) {
        onSheetGone()
    }
}
