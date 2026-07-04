package com.sza.fastmediasorter.core.ui.focus

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import java.util.WeakHashMap

/**
 * S0943: extends the in-place focus decoration to [DialogFragment] / bottom-sheet windows.
 *
 * These own a separate [android.view.Window] that the host Activity's decor listener cannot see, so
 * each gets its own [FocusDecorationController]. Non-dialog fragments render into the Activity window
 * (already covered by [FocusDecorationActivityCallbacks]) and are skipped.
 */
class FocusDecorationFragmentCallbacks : FragmentManager.FragmentLifecycleCallbacks() {

    private val controllers = WeakHashMap<Fragment, FocusDecorationController>()

    override fun onFragmentStarted(fragmentManager: FragmentManager, fragment: Fragment) {
        if (fragment !is DialogFragment) return
        // Cover only a dialog that owns its own window (not the host Activity's, already covered) and
        // is not already tracked - one combined guard keeps the return count within detekt's limit.
        val window = fragment.dialog?.window
        if (window == null || window === fragment.activity?.window || controllers.containsKey(fragment)) {
            return
        }
        val controller = FocusDecorationController(window)
        controller.attach()
        controllers[fragment] = controller
    }

    override fun onFragmentStopped(fragmentManager: FragmentManager, fragment: Fragment) {
        controllers.remove(fragment)?.detach()
    }
}
