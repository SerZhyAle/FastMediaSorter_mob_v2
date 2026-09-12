package com.sza.fastmediasorter.wear.ui.network

/**
 * S1707: whether the watch offers a way to type a new network source - a username and a password - at all.
 *
 * Wear OS review item WO-P6 refuses credential entry on the watch, and the screen behind this entry carries
 * a masked password field one tap from settings. The owner ruled on 2026-08-16 to hide the path rather than
 * remove the capability, so the screen, its ViewModel and every already-saved source stay exactly as they
 * are - only the way in disappears from a store build.
 *
 * A predicate over a passed-in flag rather than a direct build-constant read, because a constant read inside
 * a composable is the same answer in every test run, and the direction that must never regress is the one a
 * debug test cannot otherwise reach.
 *
 * S2486 changed WHICH flag arrives here. It used to be `BuildConfig.DEBUG`, which answers "is this a debug
 * build" - but the question the ruling asks is "does this build go through the store", and the two disagreed
 * in both rows that mattered: the sideload release hid the path the owner needed, while the store debug build
 * showed it. The flag is now the `noLegal`-vs-`standard` answer carried by `WearRestrictedCapabilities`.
 */

object NetworkSourceEntry {

    fun isOffered(offersCredentialEntry: Boolean): Boolean {
        return offersCredentialEntry
    }
}
