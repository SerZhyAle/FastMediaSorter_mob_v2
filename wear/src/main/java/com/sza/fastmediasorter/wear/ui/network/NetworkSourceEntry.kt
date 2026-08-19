package com.sza.fastmediasorter.wear.ui.network

/**
 * S1707: whether the watch offers a way to type a new network source - a username and a password - at all.
 *
 * Wear OS review item WO-P6 refuses credential entry on the watch, and the screen behind this entry carries
 * a masked password field one tap from settings. The owner ruled on 2026-08-16 to hide the path rather than
 * remove the capability, so the screen, its ViewModel and every already-saved source stay exactly as they
 * are - only the way in disappears from a store build.
 *
 * A predicate over a passed-in flag rather than a direct `BuildConfig.DEBUG` read, because a build constant
 * read inside a composable is the same answer in every test run, and the direction that must never regress
 * is the one a debug test cannot otherwise reach.
 */
import timber.log.Timber

object NetworkSourceEntry {

    fun isOffered(isDebugBuild: Boolean): Boolean {
        Timber.d("S1707: NetworkSourceEntry.isOffered=$isDebugBuild")
        return isDebugBuild
    }
}
