package com.sza.fastmediasorter.ui.launcher.helpers

import androidx.fragment.app.FragmentManager
import com.sza.fastmediasorter.ui.launcher.menu.LauncherAllAppsFragment
import com.sza.fastmediasorter.ui.launcher.menu.LauncherStartMenuFragment

/**
 * S2256: the home surface's two modal sheets, and the single-instance guard both of them need.
 *
 * The desktop stays touchable behind either sheet, and each is reachable from several places at once -
 * the taskbar button, the swipe-up gesture, the desktop action catalog and now an edge gesture - so an
 * unguarded open would stack a second copy over the first.
 */
class LauncherModalSurfaceManager(private val fragmentManager: FragmentManager) {

    fun showStartMenu() {
        if (isShowing(LauncherStartMenuFragment.TAG)) return
        LauncherStartMenuFragment().show(fragmentManager, LauncherStartMenuFragment.TAG)
    }

    /** S1401: the app list, reached from the taskbar button and from the swipe-up gesture alike. */
    fun showAllApps() {
        if (isShowing(LauncherAllAppsFragment.TAG)) return
        LauncherAllAppsFragment().show(fragmentManager, LauncherAllAppsFragment.TAG)
    }

    private fun isShowing(tag: String): Boolean = fragmentManager.findFragmentByTag(tag) != null
}
